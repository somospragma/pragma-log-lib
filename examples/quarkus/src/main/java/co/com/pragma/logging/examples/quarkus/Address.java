package co.com.pragma.logging.examples.quarkus;

/**
 * No masking annotations, so in native mode it is not registered for reflection and is logged as
 * {@code <Address>}. Add {@code @RegisterForReflection} to log its fields there too.
 */
public record Address(String city) {
}
