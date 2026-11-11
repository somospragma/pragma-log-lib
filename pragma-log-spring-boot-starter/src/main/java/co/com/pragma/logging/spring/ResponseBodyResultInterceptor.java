package co.com.pragma.logging.spring;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.http.HttpEntity;
import org.springframework.web.reactive.HandlerResult;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Spring WebFlux: hands the object a controller returns to {@link PragmaLogContextWebFilter}, so the response body
 * of the completion log is masked by annotations too (ENR-04). Wraps {@code handleResult} of the
 * {@code @ResponseBody} and {@code ResponseEntity} result handlers; the body is never changed.
 */
final class ResponseBodyResultInterceptor implements MethodInterceptor {

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Object[] arguments = invocation.getArguments();
        if (arguments.length == 2 && arguments[0] instanceof ServerWebExchange exchange
                && arguments[1] instanceof HandlerResult result
                && exchange.getAttribute(ResponseBodyObject.ATTRIBUTE) instanceof ResponseBodyObject holder) {
            arguments[1] = recording(result, holder);
        }
        return invocation.proceed();
    }

    private static HandlerResult recording(HandlerResult result, ResponseBodyObject holder) {
        Object value = result.getReturnValue();
        Object recorded;
        if (value instanceof Mono<?> mono) {
            recorded = mono.doOnNext(body -> holder.set(body(body)));
        } else if (value instanceof Flux<?> flux) {
            recorded = flux.doOnNext(holder::add);
        } else {
            holder.set(body(value));
            return result;
        }
        HandlerResult copy = new HandlerResult(result.getHandler(), recorded, result.getReturnTypeSource(),
                result.getBindingContext());
        if (result.getExceptionHandler() != null) {
            copy.setExceptionHandler(result.getExceptionHandler());
        }
        return copy;
    }

    private static Object body(Object value) {
        return value instanceof HttpEntity<?> entity ? entity.getBody() : value;
    }
}
