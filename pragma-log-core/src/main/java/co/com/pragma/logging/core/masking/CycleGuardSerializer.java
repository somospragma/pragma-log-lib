package co.com.pragma.logging.core.masking;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.util.NameTransformer;

/**
 * Wraps bean serializers to stop on reference cycles (MSK-06) and on excessive nesting,
 * writing a marker instead of failing.
 */
final class CycleGuardSerializer extends ValueSerializer<Object> {

    static final String CIRCULAR = "[circular]";
    static final String MAX_DEPTH = "[max-depth]";

    private static final Object STATE_KEY = CycleGuardSerializer.class;

    private final ValueSerializer<Object> delegate;
    private final int maxDepth;

    CycleGuardSerializer(ValueSerializer<Object> delegate, int maxDepth) {
        this.delegate = delegate;
        this.maxDepth = maxDepth;
    }

    @SuppressWarnings("unchecked")
    static ValueSerializer<?> wrap(ValueSerializer<?> serializer, int maxDepth) {
        if (serializer instanceof CycleGuardSerializer) {
            return serializer;
        }
        return new CycleGuardSerializer((ValueSerializer<Object>) serializer, maxDepth);
    }

    @Override
    public void serialize(Object value, JsonGenerator gen, SerializationContext ctxt) {
        Visits visits = visits(ctxt);
        if (visits.enter(value, gen, maxDepth)) {
            try {
                delegate.serialize(value, gen, ctxt);
            } finally {
                visits.exit(value);
            }
        }
    }

    @Override
    public void serializeWithType(Object value, JsonGenerator gen, SerializationContext ctxt, TypeSerializer typeSer) {
        Visits visits = visits(ctxt);
        if (visits.enter(value, gen, maxDepth)) {
            try {
                delegate.serializeWithType(value, gen, ctxt, typeSer);
            } finally {
                visits.exit(value);
            }
        }
    }

    @Override
    public void resolve(SerializationContext ctxt) {
        delegate.resolve(ctxt);
    }

    @Override
    public ValueSerializer<?> createContextual(SerializationContext ctxt, BeanProperty property) {
        ValueSerializer<?> contextual = delegate.createContextual(ctxt, property);
        return contextual == delegate ? this : wrap(contextual, maxDepth);
    }

    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return delegate.unwrappingSerializer(unwrapper);
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return delegate.isEmpty(ctxt, value);
    }

    @Override
    public boolean usesObjectId() {
        return delegate.usesObjectId();
    }

    @Override
    public Class<?> handledType() {
        return delegate.handledType();
    }

    @Override
    public Iterator<PropertyWriter> properties() {
        return delegate.properties();
    }

    @Override
    public ValueSerializer<?> getDelegatee() {
        return delegate;
    }

    private static Visits visits(SerializationContext ctxt) {
        Visits visits = (Visits) ctxt.getAttribute(STATE_KEY);
        if (visits == null) {
            visits = new Visits();
            ctxt.setAttribute(STATE_KEY, visits);
        }
        return visits;
    }

    /** Objects currently being written in this serialization call. */
    private static final class Visits {
        private final Map<Object, Boolean> inProgress = new IdentityHashMap<>();

        boolean enter(Object value, JsonGenerator gen, int maxDepth) {
            if (inProgress.containsKey(value)) {
                gen.writeString(CIRCULAR);
                return false;
            }
            if (inProgress.size() >= maxDepth) {
                gen.writeString(MAX_DEPTH);
                return false;
            }
            inProgress.put(value, Boolean.TRUE);
            return true;
        }

        void exit(Object value) {
            inProgress.remove(value);
        }
    }
}
