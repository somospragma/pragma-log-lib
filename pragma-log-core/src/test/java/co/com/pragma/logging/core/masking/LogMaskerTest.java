package co.com.pragma.logging.core.masking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;

class LogMaskerTest {

    private final LogMasker masker = new LogMasker(MaskingSettings.defaults());

    // ---- Test models -------------------------------------------------------------------------

    static class User {
        String name = "Ana";
        @MaskedLogField
        String password = "s3cr3t-pass";

        @Override
        public String toString() {
            return "User{name=Ana, password=s3cr3t-pass}";
        }
    }

    static class Account {
        String owner = "Ana";
        @MaskedLogField(type = MaskType.MIDDLE, length = 4)
        long cardNumber = 4111111111111111L;
        @MaskedLogField
        Address address = new Address();
        @MaskedLogField(type = MaskType.HIDDEN)
        String cvv = "123";
    }

    static class Address {
        String street = "Calle 1 # 2-3";
    }

    /** On a class, the annotation masks each field; a field's own annotation wins (MSK-01b). */
    @MaskedLogField(type = MaskType.PREFIX, length = 2)
    static class Contact {
        String phone = "3001234567";
        String email = "ana@acme.com";
        @MaskedLogField(type = MaskType.HIDDEN)
        String note = "internal";
    }

    static class VipContact extends Contact {
        String level = "gold";
    }

    @MaskedLogField(type = MaskType.SUFFIX, length = 3)
    record Phone(String number) {
    }

    static class BaseCredentials {
        @MaskedLogField
        String secretKey = "base-secret";
    }

    static class ServiceCredentials extends BaseCredentials {
        String service = "payments";
    }

    record Card(@MaskedLogField(type = MaskType.SUFFIX, length = 3) String number, String holder) {
    }

    static class Order {
        List<Card> cards = List.of(new Card("4111", "Ana"), new Card("5500", "Luis"));
        Map<String, Object> metadata = Map.of("access_token", "tok-123", "channel", "web");
    }

    static class Node {
        String name;
        Node next;

        Node(String name) {
            this.name = name;
        }
    }

    static class Exploding {
        @JsonSerialize(using = ExplodingSerializer.class)
        String data = "SECRET-IN-FIELD";

        @Override
        public String toString() {
            return "Exploding{SECRET-IN-TOSTRING}";
        }
    }

    static class ExplodingSerializer extends ValueSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
            throw new IllegalStateException("boom");
        }
    }

    static class Incompatible {
        @JsonSerialize(using = LinkageFailingSerializer.class)
        String data = "SECRET";
    }

    /** Simulates an incompatible dependency version on the application classpath. */
    static class LinkageFailingSerializer extends ValueSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
            throw new NoClassDefFoundError("com/fasterxml/jackson/annotation/JsonApplyView");
        }
    }

    static class Unsupported {
        @JsonSerialize(using = ErrorThrowingSerializer.class)
        String data = "SECRET";
    }

    static class OutOfMemory {
        @JsonSerialize(using = ErrorThrowingSerializer.class)
        String data = "oom";
    }

    /** Simulates a native image, where reflection on an unregistered class throws an Error. */
    static class ErrorThrowingSerializer extends ValueSerializer<String> {
        @Override
        public void serialize(String value, JsonGenerator gen, SerializationContext ctxt) {
            if (value.equals("oom")) {
                throw new OutOfMemoryError("simulated");
            }
            throw new Error("Record components not available for record class");
        }
    }

    static class Custom {
        @MaskedLogField(type = MaskType.CUSTOM, strategy = "EMAIL")
        String email = "ana.perez@pragma.com.co";
        @MaskedLogField(type = MaskType.CUSTOM, strategy = "UNKNOWN")
        String unknown = "visible?";
        @MaskedLogField(type = MaskType.CUSTOM, strategy = "BROKEN")
        String broken = "visible?";
    }

    // ---- Tests -------------------------------------------------------------------------------

    @Test
    @DisplayName("an annotated class masks each of its fields; subclasses inherit it (MSK-01b)")
    void classLevelAnnotation() {
        JsonNode contact = masker.toTree(new Contact());
        JsonNode vip = masker.toTree(new VipContact());

        assertThat(contact.get("phone").asString()).isEqualTo("**01234567");
        assertThat(contact.get("email").asString()).isEqualTo("**a@acme.com");
        assertThat(contact.has("note")).isFalse();
        assertThat(vip.get("level").asString()).isEqualTo("**ld");
        assertThat(masker.toTree(new Phone("3001234567")).get("number").asString()).isEqualTo("3001234***");
    }

    @Nested
    @DisplayName("security regressions from the legacy implementation")
    class SecurityRegressions {

        @Test
        @DisplayName("masking never modifies the logged object (MSK-08)")
        void doesNotMutateOriginal() {
            User user = new User();

            masker.toTree(user);

            assertThat(user.password).isEqualTo("s3cr3t-pass");
        }

        @Test
        @DisplayName("message text never comes from toString() (MSK-07)")
        void textDoesNotUseToString() {
            String text = masker.toText(new User());

            assertThat(text).doesNotContain("s3cr3t-pass").contains("\"password\":\"***********\"");
        }

        @Test
        @DisplayName("annotated non-String fields are masked (MSK-01)")
        void masksNonStringFields() {
            JsonNode json = masker.toTree(new Account());

            assertThat(json.get("cardNumber").asString()).isEqualTo("4111********1111");
            assertThat(json.get("address").asString()).isEqualTo("********");
            assertThat(json.toString()).doesNotContain("Calle");
        }

        @Test
        @DisplayName("inherited annotated fields are masked (MSK-01)")
        void masksInheritedFields() {
            JsonNode json = masker.toTree(new ServiceCredentials());

            assertThat(json.get("secretKey").asString()).isEqualTo("***********");
            assertThat(json.get("service").asString()).isEqualTo("payments");
        }

        @Test
        @DisplayName("a serialization failure yields a placeholder, never toString() (ALR-05)")
        void failureFallsBackToPlaceholder() {
            assertThat(masker.toText(new Exploding())).isEqualTo("<Exploding>");
        }

        @Test
        @DisplayName("incompatible dependency versions yield a placeholder instead of breaking the app (ADR-15)")
        void linkageErrorFallsBackToPlaceholder() {
            assertThat(masker.toText(new Incompatible())).isEqualTo("<Incompatible>");
        }

        @Test
        @DisplayName("native-image reflection errors yield a placeholder; JVM errors such as out of memory propagate")
        void otherErrorsFallBackToPlaceholder() {
            assertThat(masker.toText(new Unsupported())).isEqualTo("<Unsupported>");
            assertThatThrownBy(() -> masker.toText(new OutOfMemory())).isInstanceOf(OutOfMemoryError.class);
        }
    }

    @Test
    @DisplayName("HIDDEN removes the field")
    void hiddenRemovesField() {
        assertThat(masker.toTree(new Account()).has("cvv")).isFalse();
    }

    @Test
    @DisplayName("records inside collections are masked (MSK-05)")
    void masksRecordsInsideCollections() {
        JsonNode cards = masker.toTree(new Order()).get("cards");

        assertThat(cards.get(0).get("number").asString()).isEqualTo("4***");
        assertThat(cards.get(1).get("holder").asString()).isEqualTo("Luis");
    }

    @Test
    @DisplayName("sensitive names are masked at any depth, map keys included (MSK-09)")
    void masksSensitiveNamesInMaps() {
        JsonNode metadata = masker.toTree(new Order()).get("metadata");

        assertThat(metadata.get("access_token").asString()).isEqualTo("*******");
        assertThat(metadata.get("channel").asString()).isEqualTo("web");
    }

    @Test
    @DisplayName("dotted and x- prefixed names are matched by their last segment (MSK-11)")
    void matchesLastSegmentOfDottedNames() {
        assertThat(masker.isSensitiveName("http.header.authorization")).isTrue();
        assertThat(masker.isSensitiveName("http.header.x-api-key")).isTrue();
        assertThat(masker.isSensitiveName("X-Auth-Token")).isTrue();
        assertThat(masker.isSensitiveName("x-access-token")).isTrue();
        assertThat(masker.isSensitiveName("user.password")).isTrue();
        assertThat(masker.isSensitiveName("http.header.x-client-id")).isFalse();
        assertThat(masker.isSensitiveName("http.path")).isFalse();
    }

    @Test
    @DisplayName("reference cycles produce a marker instead of failing (MSK-06)")
    void cyclesProduceMarker() {
        Node a = new Node("a");
        Node b = new Node("b");
        a.next = b;
        b.next = a;

        JsonNode json = masker.toTree(a);

        assertThat(json.get("next").get("next").asString()).isEqualTo(CycleGuardSerializer.CIRCULAR);
    }

    @Test
    @DisplayName("a map that contains itself produces a marker")
    void selfContainingMap() {
        Map<String, Object> map = new java.util.HashMap<>();
        map.put("self", map);

        assertThat(masker.toTree(map).get("self").asString()).isEqualTo(CycleGuardSerializer.CIRCULAR);
    }

    @Test
    @DisplayName("nesting deeper than maxDepth produces a marker")
    void deepNestingIsCut() {
        LogMasker shallow = new LogMasker(new MaskingSettings(true, '*', MaskType.FULL, 4, Set.of(), 2, Map.of()));
        Node root = new Node("1");
        root.next = new Node("2");
        root.next.next = new Node("3");

        JsonNode json = shallow.toTree(root);

        assertThat(json.get("next").get("next").asString()).isEqualTo(CycleGuardSerializer.MAX_DEPTH);
    }

    @Test
    @DisplayName("custom strategies are applied; unknown ones mask fully and broken ones never leak")
    void customStrategies() {
        MaskingStrategy email = (value, c, n) -> value.charAt(0) + "***" + value.substring(value.indexOf('@'));
        MaskingStrategy broken = (value, c, n) -> {
            throw new IllegalStateException("bug");
        };
        LogMasker custom = new LogMasker(new MaskingSettings(true, '*', MaskType.FULL, 4, Set.of(), 10,
                Map.of("EMAIL", email, "BROKEN", broken)));

        JsonNode json = custom.toTree(new Custom());

        assertThat(json.get("email").asString()).isEqualTo("a***@pragma.com.co");
        assertThat(json.get("unknown").asString()).isEqualTo("********");
        assertThat(json.get("broken").asString()).isEqualTo(TextMasker.UNMASKABLE);
    }

    @Test
    @DisplayName("when masking is disabled values are written as they are")
    void disabledMasking() {
        LogMasker disabled = new LogMasker(new MaskingSettings(false, '*', MaskType.FULL, 4,
                MaskingSettings.DEFAULT_FIELDS, 10, Map.of()));

        assertThat(disabled.toTree(new User()).get("password").asString()).isEqualTo("s3cr3t-pass");
    }

    @Test
    @DisplayName("scalars are rendered as plain text")
    void scalarsAsText() {
        assertThat(masker.toText(42)).isEqualTo("42");
        assertThat(masker.toText("hello")).isEqualTo("hello");
        assertThat(masker.toText(null)).isEqualTo("null");
        assertThat(masker.toText(new ArrayList<>(List.of(1, 2)))).isEqualTo("[1,2]");
    }
}
