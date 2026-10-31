package co.com.pragma.logging.spring;

import io.micrometer.context.ContextRegistry;
import java.util.List;
import org.springframework.beans.factory.DisposableBean;
import reactor.core.publisher.Hooks;

/**
 * Registers a {@link ContextKeyMdcAccessor} for each {@code pragma.log.context-keys} entry, so values the
 * application puts in the Reactor Context show up as log fields on any thread (CTX-08). Works for every
 * reactive flow, not only HTTP requests. Accessors are removed when the application context closes.
 */
final class PragmaContextKeysRegistrar implements DisposableBean {

    private final List<String> keys;

    PragmaContextKeysRegistrar(List<String> keys) {
        this.keys = List.copyOf(keys);
        for (String key : this.keys) {
            if (key == null || key.isBlank() || RequestLogContext.isContextKey(key)
                    || PragmaMdcThreadLocalAccessor.KEY.equals(key)) {
                throw new IllegalStateException("Invalid " + PragmaLogProperties.PREFIX
                        + ".context-keys entry '" + key + "': blank or reserved by pragma-log");
            }
        }
        if (!this.keys.isEmpty()) {
            this.keys.forEach(key -> ContextRegistry.getInstance()
                    .registerThreadLocalAccessor(new ContextKeyMdcAccessor(key)));
            Hooks.enableAutomaticContextPropagation();
        }
    }

    @Override
    public void destroy() {
        keys.forEach(ContextRegistry.getInstance()::removeThreadLocalAccessor);
    }
}
