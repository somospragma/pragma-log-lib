package co.com.pragma.logging.quarkus.slf4j;

import java.util.Deque;
import java.util.Map;
import org.jboss.logmanager.MDC;
import org.slf4j.helpers.ThreadLocalMapOfStacks;
import org.slf4j.spi.MDCAdapter;

/**
 * SLF4J MDC backed by the JBoss LogManager MDC, which Quarkus stores in the Vert.x duplicated context: values
 * follow a request across event-loop and worker threads.
 */
final class JBossMdcAdapter implements MDCAdapter {

    private final ThreadLocalMapOfStacks stacks = new ThreadLocalMapOfStacks();

    @Override
    public void put(String key, String value) {
        if (value == null) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    @Override
    public String get(String key) {
        return MDC.get(key);
    }

    @Override
    public void remove(String key) {
        MDC.remove(key);
    }

    @Override
    public void clear() {
        MDC.clear();
    }

    @Override
    public Map<String, String> getCopyOfContextMap() {
        return MDC.copy();
    }

    @Override
    public void setContextMap(Map<String, String> contextMap) {
        MDC.clear();
        contextMap.forEach(MDC::put);
    }

    @Override
    public void pushByKey(String key, String value) {
        stacks.pushByKey(key, value);
    }

    @Override
    public String popByKey(String key) {
        return stacks.popByKey(key);
    }

    @Override
    public Deque<String> getCopyOfDequeByKey(String key) {
        return stacks.getCopyOfDequeByKey(key);
    }

    @Override
    public void clearDequeByKey(String key) {
        stacks.clearDequeByKey(key);
    }
}
