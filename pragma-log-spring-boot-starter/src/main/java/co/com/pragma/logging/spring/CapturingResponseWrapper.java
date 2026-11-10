package co.com.pragma.logging.spring;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.Charset;

/**
 * Copies the first bytes of the response body as the application writes it (ENR-04). Everything is written
 * through to the client at once: the response is not buffered.
 */
final class CapturingResponseWrapper extends HttpServletResponseWrapper {

    private final BodyBuffer body;
    private ServletOutputStream output;
    private PrintWriter writer;

    CapturingResponseWrapper(HttpServletResponse response, int limit) {
        super(response);
        this.body = new BodyBuffer(limit);
    }

    BodyBuffer body() {
        return body;
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (output == null) {
            output = new CapturingOutputStream(super.getOutputStream(), body);
        }
        return output;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(getOutputStream(), Charset.forName(getCharacterEncoding())),
                    false);
        }
        return writer;
    }

    @Override
    public void flushBuffer() throws IOException {
        if (writer != null) {
            writer.flush();
        }
        super.flushBuffer();
    }

    /** Flushes what the application wrote through the writer, before the body is read for the log. */
    void flushWriter() {
        if (writer != null) {
            writer.flush();
        }
    }

    private static final class CapturingOutputStream extends ServletOutputStream {

        private final ServletOutputStream delegate;
        private final BodyBuffer body;

        CapturingOutputStream(ServletOutputStream delegate, BodyBuffer body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            body.write(b);
        }

        @Override
        public void write(byte[] buffer, int offset, int length) throws IOException {
            delegate.write(buffer, offset, length);
            body.write(buffer, offset, length);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setWriteListener(WriteListener listener) {
            delegate.setWriteListener(listener);
        }
    }
}
