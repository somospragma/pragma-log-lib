package co.com.pragma.logging.examples.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs the example app with the extension and checks the JSON it writes (ADR-11, INT-04). */
@QuarkusTest
class QuarkusLoggingTest {

    private static final Path LOG = Path.of("build/test-logs/app.log");

    /** Whether a log line comes from the app under test; the native tests share the file. */
    protected boolean fromApp(JsonNode json) {
        return true;
    }

    @Test
    @DisplayName("SLF4J arguments, key-values and JBoss Logging parameters are masked (MSK-07)")
    void masksEverything() throws Exception {
        given().header("X-Request-Id", "req-1").get("/customers/1").then().statusCode(200);

        JsonNode found = line("Customer found: ");
        assertThat(found.get("message").asString())
                .contains("4111********1111").contains("\"password\":\"******\"");
        assertThat(found.get("app").asString()).isEqualTo("customers-service");
        assertThat(found.get("env").asString()).isEqualTo("local");
        assertThat(found.get("level").asString()).isEqualTo("INFO");
        assertThat(found.get("host").asString()).isNotBlank();

        JsonNode lookup = line("Lookup finished");
        assertThat(lookup.get("customerId").asString()).isEqualTo("1");
        assertThat(lookup.get("customer").get("cardNumber").asString()).isEqualTo("4111********1111");
        assertThat(lookup.get("customer").get("password").asString()).isEqualTo("******");

        assertThat(line("Audit ").get("message").asString())
                .contains("4111********1111").endsWith("(attempt 1)");
        assertThat(line("Gateway note: ").get("message").asString()).isEqualTo("Gateway note: charged card 4111********1111");
        assertThat(Files.readString(LOG)).doesNotContain("s3cr3t").doesNotContain("4111111111111111");
    }

    @Test
    @DisplayName("a class without masking annotations is logged as JSON")
    void plainClass() throws Exception {
        given().get("/customers/9").then().statusCode(200);

        assertThat(line("Shipping to ").get("message").asString()).isEqualTo("Shipping to " + expectedAddress());
    }

    /** In native mode classes not registered for reflection become {@code <ClassName>}. */
    protected String expectedAddress() {
        return "{\"city\":\"Medellin\"}";
    }

    @Test
    @DisplayName("the process id is written (FLD-12)")
    void pid() throws Exception {
        given().get("/customers/6").then().statusCode(200);

        assertThat(line("Customer found: {\"id\":\"6\"").get("pid").asLong()).isEqualTo(ProcessHandle.current().pid());
    }

    @Test
    @DisplayName("a log above max-log-size is written whole and the configured listener is told (ALR-01, ALR-02)")
    void largeLogAlert() throws Exception {
        given().get("/customers/7/history").then().statusCode(200).body(org.hamcrest.Matchers.equalTo("5000"));

        assertThat(line("History loaded").get("movements").size()).isEqualTo(5_000);
        assertThat(line("Large log from co.com.pragma.logging.examples.quarkus.CustomerResource: ")
                .get("level").asString()).isEqualTo("WARN");
    }

    @Test
    @DisplayName("the request context and completion log are written, also from other threads (CTX-06, ENR-01)")
    void requestContext() throws Exception {
        given().header("X-Request-Id", "req-2").header("X-Client-Id", "mobile")
                .header("Authorization", "Bearer abc.def").get("/customers/2").then().statusCode(200)
                .header("X-Request-Id", "req-2");
        given().header("X-Request-Id", "req-3").get("/customers/3/async").then().statusCode(200);

        JsonNode found = line("Customer found: {\"id\":\"2\"");
        assertThat(found.get("requestId").asString()).isEqualTo("req-2");
        assertThat(found.get("http.method").asString()).isEqualTo("GET");
        assertThat(found.get("http.path").asString()).isEqualTo("/customers/2");
        assertThat(found.get("http.header.x-client-id").asString()).isEqualTo("mobile");
        assertThat(found.get("http.header.authorization").asString()).matches("\\*+");

        JsonNode completion = line("HTTP GET /customers/2 200");
        assertThat(completion.get("http.status").asInt()).isEqualTo(200);
        assertThat(completion.get("http.durationMs").isNumber()).isTrue();
        assertThat(completion.get("requestId").asString()).isEqualTo("req-2");

        JsonNode asyncLoaded = line("Async loaded 3");
        assertThat(asyncLoaded.get("requestId").asString()).isEqualTo("req-3");
        assertThat(asyncLoaded.get("thread").asString()).isNotEqualTo(line("Async lookup 3").get("thread").asString());
        assertThat(Files.readString(LOG)).doesNotContain("abc.def");
    }

    @Test
    @DisplayName("exceptions get the error summary and a stack trace; failed requests log 500 and 404")
    void errors() throws Exception {
        given().header("X-Request-Id", "req-4").get("/customers/fail").then().statusCode(500);
        given().get("/missing").then().statusCode(404);
        line("HTTP GET /missing 404"); // the last log of these requests: everything before it is written

        JsonNode error = lines().stream()
                .filter(json -> json.has("error") && json.get("error").get("type").asString().endsWith("IllegalStateException"))
                .findFirst().orElseThrow(() -> new AssertionError("No error log in:\n" + content()));
        assertThat(error.get("error").get("rootCause").asString())
                .isEqualTo("java.net.SocketTimeoutException: Read timed out");
        assertThat(error.get("stacktrace").asString()).contains("Caused by: java.net.SocketTimeoutException");
        assertThat(line("HTTP GET /customers/fail 500").get("requestId").asString()).isEqualTo("req-4");
        assertThat(line("HTTP GET /missing 404").get("http.status").asInt()).isEqualTo(404);
    }

    @Test
    @DisplayName("logs outside a request carry no request context")
    void noLeak() throws Exception {
        given().header("X-Request-Id", "req-5").get("/customers/5").then().statusCode(200);

        LoggerFactory.getLogger(QuarkusLoggingTest.class).info("Outside any request");

        assertThat(line("Outside any request").has("requestId")).isFalse();
    }

    /** Waits a little: the request completion log is written right after the response is sent. */
    protected JsonNode line(String messagePrefix) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            Optional<JsonNode> found = lines().stream()
                    .filter(json -> json.path("message").asString("").startsWith(messagePrefix))
                    .findFirst();
            if (found.isPresent()) {
                return found.get();
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No JSON log starting with '" + messagePrefix + "' in:\n" + content());
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }

    private List<JsonNode> lines() throws IOException {
        return Files.readAllLines(LOG).stream()
                .filter(line -> line.startsWith("{"))
                .map(line -> JsonMapper.shared().readTree(line))
                .filter(this::fromApp)
                .toList();
    }

    private String content() {
        try {
            return Files.readString(LOG);
        } catch (IOException e) {
            return e.toString();
        }
    }
}
