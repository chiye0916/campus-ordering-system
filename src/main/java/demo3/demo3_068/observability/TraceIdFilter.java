package demo3.demo3_068.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class TraceIdFilter extends OncePerRequestFilter {

    private final TraceContext traceContext;

    public TraceIdFilter(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try (TraceContext.Scope scope = traceContext.open(request.getHeader(TraceContext.TRACE_HEADER))) {
            response.setHeader(TraceContext.TRACE_HEADER, scope.traceId());
            filterChain.doFilter(request, response);
        }
    }
}
