package co.com.pragma.logging.examples.quarkus;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import java.nio.file.Path;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The same checks against the native executable (INT-04): masking through Jackson, the SLF4J binding, request
 * context, alert listeners and timestamp constants must work without the reflection of JVM mode.
 */
@QuarkusIntegrationTest
class QuarkusLoggingIT extends QuarkusLoggingTest {

    @Override
    protected Path logFile() {
        return Path.of("build/test-logs/native.log");
    }

    @Test
    @DisplayName("timestamp constants and zones work in native mode (FMT-06)")
    void timestampConstant() throws Exception {
        given().get("/customers/8").then().statusCode(200);

        assertThat(line("Customer found: {\"id\":\"8\"").get("timestamp").asString())
                .matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
    }

    @Test
    @Override
    @Disabled("the app runs in its own process")
    void pid() {
    }

    @Test
    @Override
    @Disabled("logs from the test process do not reach the app")
    void noLeak() {
    }
}
