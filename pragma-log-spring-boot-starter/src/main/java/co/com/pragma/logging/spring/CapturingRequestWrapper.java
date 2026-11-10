package co.com.pragma.logging.spring;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Copies the first bytes of the request body as the application reads it (ENR-04); the body is not read ahead. */
final class CapturingRequestWrapper extends HttpServletRequestWrapper {

    private final BodyBuffer body;
    private ServletInputStream input;
    private BufferedReader reader;

    CapturingRequestWrapper(HttpServletRequest request, int limit) {
        super(request);
        this.body = new BodyBuffer(limit);
    }

    BodyBuffer body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (input == null) {
            input = new CapturingInputStream(super.getInputStream(), body);
        }
        return input;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.ISO_8859_1 : Charset.forName(encoding);
            reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
        return reader;
    }

    private static final class CapturingInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final BodyBuffer body;

        CapturingInputStream(ServletInputStream delegate, BodyBuffer body) {
            this.delegate = delegate;
            this.body = body;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b >= 0) {
                body.write(b);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int count = delegate.read(buffer, offset, length);
            if (count > 0) {
                body.write(buffer, offset, count);
            }
            return count;
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener listener) {
            delegate.setReadListener(listener);
        }
    }
}
