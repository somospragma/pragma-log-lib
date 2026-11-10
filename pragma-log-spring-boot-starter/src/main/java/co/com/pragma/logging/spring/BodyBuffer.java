package co.com.pragma.logging.spring;

import java.util.Arrays;

/**
 * Keeps the first bytes of a body as it is read or written, and counts the rest (ENR-04). Never holds more than
 * {@code limit + 1} bytes, so large or streamed bodies do not fill the memory. Not thread-safe: one per body.
 */
final class BodyBuffer {

    private final int limit;
    private byte[] bytes = new byte[0];
    private int kept;
    private long total;

    BodyBuffer(int limit) {
        this.limit = limit;
    }

    void write(int b) {
        if (kept <= limit) {
            ensure(kept + 1);
            bytes[kept++] = (byte) b;
        }
        total++;
    }

    void write(byte[] source, int offset, int length) {
        int room = Math.max(0, limit + 1 - kept);
        int copied = Math.min(room, length);
        if (copied > 0) {
            ensure(kept + copied);
            System.arraycopy(source, offset, bytes, kept, copied);
            kept += copied;
        }
        total += length;
    }

    /** The kept bytes: up to {@code limit + 1}, so a cut body can be told from a complete one. */
    byte[] bytes() {
        return Arrays.copyOf(bytes, kept);
    }

    long total() {
        return total;
    }

    private void ensure(int size) {
        if (bytes.length < size) {
            bytes = Arrays.copyOf(bytes, Math.min(limit + 1, Math.max(size, bytes.length * 2 + 64)));
        }
    }
}
