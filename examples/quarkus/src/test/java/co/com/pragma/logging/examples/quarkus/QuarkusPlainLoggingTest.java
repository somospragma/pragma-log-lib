package co.com.pragma.logging.examples.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The example app with {@code pragma.log.format=plain}: readable text, same masking, no colors in files. */
@QuarkusTest
@TestProfile(QuarkusPlainLoggingTest.Plain.class)
class QuarkusPlainLoggingTest {

    private static final Path LOG = Path.of("build/test-logs/plain.log");

    public static class Plain implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("pragma.log.format", "plain", "pragma.log.color.mode", "always",
                    "pragma.log.timestamp.pattern", "yyyy-MM-dd'T'HH:mm:ss", "pragma.log.timestamp.zone", "UTC",
                    "quarkus.log.file.path", LOG.toString());
        }
    }

    @Test
    @DisplayName("plain text keeps masking and the request context, and the file has no colors (FMT-01, FMT-05, FMT-06)")
    void plainText() throws Exception {
        given().header("X-Request-Id", "req-p1").get("/customers/1").then().statusCode(200);

        String found = line("Customer found: ");
        assertThat(found)
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}  INFO \\[.+] .+ : Customer found: .*")
                .contains("4111********1111").contains("\"password\":\"******\"").contains("requestId=req-p1");
        assertThat(line("Lookup finished")).contains("customerId=1").contains("\"cardNumber\":\"4111********1111\"");
        line("HTTP GET /customers/1 200");
        assertThat(Files.readString(LOG)).doesNotContain("s3cr3t").doesNotContain("4111111111111111")
                .doesNotContain("\u001b[");
    }

    private static String line(String text) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (true) {
            if (Files.exists(LOG)) {
                var found = Files.readAllLines(LOG).stream().filter(line -> line.contains(" : " + text)).findFirst();
                if (found.isPresent()) {
                    return found.get();
                }
            }
            if (System.nanoTime() > deadline) {
                throw new AssertionError("No log with '" + text + "' in " + LOG);
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
    }
}
