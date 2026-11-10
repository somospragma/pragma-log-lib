package co.com.pragma.logging.logback;

/**
 * {@link PragmaStructuredEncoder} writing JSON, one line per event; kept for existing {@code logback.xml} files.
 *
 * <pre>{@code
 * <encoder class="co.com.pragma.logging.logback.PragmaJsonEncoder">
 *   <appName>my-service</appName>
 *   <maskFields>password,cardNumber,documentId</maskFields>
 * </encoder>
 * }</pre>
 */
public class PragmaJsonEncoder extends PragmaStructuredEncoder {
}
