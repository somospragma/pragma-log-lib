package co.com.pragma.logging.spring;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Spring MVC: hands the object a controller returns to {@link PragmaLogContextFilter}, so the response body of the
 * completion log is masked by annotations too (ENR-04). The body is never changed.
 */
@ControllerAdvice
public class PragmaLogResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (request instanceof ServletServerHttpRequest servlet
                && servlet.getServletRequest().getAttribute(ResponseBodyObject.ATTRIBUTE)
                        instanceof ResponseBodyObject holder) {
            try {
                holder.set(body);
            } catch (RuntimeException | LinkageError e) {
                // Logging must never break the response: the completion log falls back to the bytes.
            }
        }
        return body;
    }
}
