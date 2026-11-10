package co.com.pragma.logging.examples.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Quarkus's own asynchronous logging (quarkus.log.file.async) with the pragma-log formatter. */
@QuarkusTest
@TestProfile(QuarkusAsyncLoggingTest.Async.class)
class QuarkusAsyncLoggingTest {

    private static final Path LOG = Path.of("build/test-logs/async.log");

    public static class Async implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.log.file.async", "true", "quarkus.log.file.path", LOG.toString());
        }
    }

    @Test
    @DisplayName("asynchronous file logging keeps masking and the request context")
    void asyncFile() throws Exception {
        given().header("X-Request-Id", "req-async").get("/customers/11").then().statusCode(200);

        JsonNode found = waitFor("Customer found: {\"id\":\"11\"");
        assertThat(found.get("requestId").asString()).isEqualTo("req-async");
        assertThat(found.get("message").asString()).contains("4111********1111");
        assertThat(Files.readString(LOG)).doesNotContain("4111111111111111").doesNotContain("s3cr3t");
    }

    private static JsonNode waitFor(String prefix) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            if (Files.exists(LOG)) {
                var line = Files.readAllLines(LOG).stream()
                        .filter(text -> text.startsWith("{"))
                        .map(text -> JsonMapper.shared().readTree(text))
                        .filter(json -> json.path("message").asString("").startsWith(prefix))
                        .findFirst();
                if (line.isPresent()) {
                    return line.get();
                }
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No log starting with '" + prefix + "' in " + LOG);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }
}
