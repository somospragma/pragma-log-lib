package co.com.pragma.logging.spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResponseBodyObjectTest {

    @Test
    @DisplayName("works in Spring MVC apps without Reactor on the classpath")
    void worksWithoutReactor() throws Exception {
        try (WithoutReactor loader = new WithoutReactor()) {
            Class<?> type = loader.loadClass(ResponseBodyObject.class.getName());
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object holder = constructor.newInstance();
            var set = type.getDeclaredMethod("set", Object.class);
            set.setAccessible(true);
            set.invoke(holder, Map.of("status", "ok"));
            var value = type.getDeclaredMethod("value");
            value.setAccessible(true);

            assertThat(value.invoke(holder)).isEqualTo(Map.of("status", "ok"));
        }
    }

    @Test
    void keepsObjectsButNotTextOrBinaryBodies() {
        assertThat(ResponseBodyObject.loggable(Map.of())).isTrue();
        assertThat(ResponseBodyObject.loggable("text")).isFalse();
        assertThat(ResponseBodyObject.loggable(new byte[0])).isFalse();
        assertThat(ResponseBodyObject.loggable(reactor.core.publisher.Mono.just(1))).isFalse();
    }

    @Test
    void summarizesLongMultiValueBodies() {
        ResponseBodyObject holder = new ResponseBodyObject();
        for (int i = 0; i < ResponseBodyObject.MAX_ELEMENTS + 5; i++) {
            holder.add(Map.of("i", i));
        }
        assertThat((java.util.List<?>) holder.value()).hasSize(ResponseBodyObject.MAX_ELEMENTS + 1)
                .last().isEqualTo("...[5 more elements]");
    }

    /** Loads the starter class itself and hides Reactor and Reactive Streams, like an MVC-only app. */
    private static final class WithoutReactor extends ClassLoader implements AutoCloseable {

        WithoutReactor() {
            super(ResponseBodyObjectTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("reactor.") || name.startsWith("org.reactivestreams.")) {
                throw new ClassNotFoundException(name);
            }
            if (!name.startsWith(ResponseBodyObject.class.getName())) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded != null) {
                    return loaded;
                }
                String resource = name.replace('.', '/') + ".class";
                try (InputStream in = getParent().getResourceAsStream(resource)) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(name, bytes, 0, bytes.length);
                } catch (IOException e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        }

        @Override
        public void close() {
        }
    }
}
