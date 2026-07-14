package demo3.demo3_068.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter(new TraceContext());

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validTraceIdPropagatesToResponseAndMdcDuringRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dish/list");
        request.addHeader(TraceContext.TRACE_HEADER, "trace-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> assertThat(MDC.get(TraceContext.TRACE_ID)).isEqualTo("trace-1234");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).isEqualTo("trace-1234");
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }

    @Test
    void invalidTraceIdIsReplacedInResponse() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dish/list");
        request.addHeader(TraceContext.TRACE_HEADER, "bad trace");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(TraceContext.TRACE_HEADER))
                .isNotEqualTo("bad trace")
                .matches("[A-Za-z0-9_-]{8,64}");
        verify(chain).doFilter(request, response);
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }

    @Test
    void missingTraceIdIsGenerated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/dish/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> assertThat(MDC.get(TraceContext.TRACE_ID)).isNotBlank());

        assertThat(response.getHeader(TraceContext.TRACE_HEADER)).matches("[A-Za-z0-9_-]{8,64}");
        assertThat(MDC.get(TraceContext.TRACE_ID)).isNull();
    }
}
