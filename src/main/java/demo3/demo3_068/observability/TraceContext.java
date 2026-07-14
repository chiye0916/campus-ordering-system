package demo3.demo3_068.observability;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class TraceContext {

    public static final String TRACE_ID = "traceId";
    public static final String TRACE_HEADER = "X-Trace-Id";

    private static final Pattern SAFE_TRACE_ID = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    public String currentTraceId() {
        return MDC.get(TRACE_ID);
    }

    public String acceptOrGenerate(String candidate) {
        if (candidate != null && SAFE_TRACE_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return generateTraceId();
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void put(String traceId) {
        MDC.put(TRACE_ID, acceptOrGenerate(traceId));
    }

    public void clear() {
        MDC.remove(TRACE_ID);
    }

    public Scope open(String candidate) {
        String previous = MDC.get(TRACE_ID);
        String traceId = acceptOrGenerate(candidate);
        MDC.put(TRACE_ID, traceId);
        return new Scope(traceId, previous);
    }

    public final class Scope implements AutoCloseable {
        private final String traceId;
        private final String previous;

        private Scope(String traceId, String previous) {
            this.traceId = traceId;
            this.previous = previous;
        }

        public String traceId() {
            return traceId;
        }

        @Override
        public void close() {
            if (previous == null) {
                clear();
            } else {
                MDC.put(TRACE_ID, previous);
            }
        }
    }
}
