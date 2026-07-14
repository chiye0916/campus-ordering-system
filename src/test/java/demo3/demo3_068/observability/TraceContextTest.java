package demo3.demo3_068.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTest {

    private final TraceContext traceContext = new TraceContext();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validTraceIdIsAccepted() {
        assertThat(traceContext.acceptOrGenerate("abc_DEF-1234")).isEqualTo("abc_DEF-1234");
    }

    @Test
    void invalidTraceIdIsReplaced() {
        String generated = traceContext.acceptOrGenerate("bad\ntrace");

        assertThat(generated).isNotEqualTo("bad\ntrace");
        assertThat(generated).matches("[A-Za-z0-9_-]{8,64}");
    }

    @Test
    void scopePutsAndClearsMdc() {
        try (TraceContext.Scope scope = traceContext.open("trace-1234")) {
            assertThat(scope.traceId()).isEqualTo("trace-1234");
            assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo("trace-1234");
        }

        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }
}
