package com.antshorttv.operations.telemetry;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.antshorttv.observability.DatabaseQueryObservation;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.*;
import org.springframework.web.servlet.HandlerMapping;

class OperationsHttpFilterTest {
    @Test void excludesMultipartUploadsAndSynchronousMediaOrArchiveDownloads() throws Exception {
        var telemetry=enabledTelemetry();var filter=new OperationsHttpFilter(telemetry);
        var upload=new MockHttpServletRequest("POST","/api/uploads");upload.setContentType("multipart/form-data; boundary=test");filter.doFilter(upload,new MockHttpServletResponse(),(req,res)->{});
        for(String type:new String[]{"video/mp4","audio/mpeg","image/png","application/zip","application/octet-stream"}) {
            var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/download/1"),response,(req,res)->response.setContentType(type));
        }
        var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/download/2"),response,(req,res)->{response.setContentType("text/csv");response.setHeader("Content-Disposition","attachment; filename=export.csv");});
        verifyNoInteractions(telemetry);
    }
    OperationsTelemetry enabledTelemetry(){var telemetry=mock(OperationsTelemetry.class);telemetry.enabled=true;return telemetry;}
    @Test void recordsNormalizedPatternAndSynchronousSqlOnly() throws Exception {
        var telemetry=enabledTelemetry();var filter=new OperationsHttpFilter(telemetry);
        var request=new MockHttpServletRequest("GET","/api/task/123");var response=new MockHttpServletResponse();
        filter.doFilter(request,response,(req,res)->{request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,"/api/task/{id}");DatabaseQueryObservation.recordQuery(2_000_000);});
        var capture=ArgumentCaptor.forClass(OperationsTelemetry.HttpObservation.class);verify(telemetry).recordHttp(capture.capture());
        assertThat(capture.getValue().route()).isEqualTo("/api/task/{id}");assertThat(capture.getValue().sqlCount()).isEqualTo(1);assertThat(capture.getValue().sqlMs()).isEqualTo(2);
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();assertThat(DatabaseQueryObservation.snapshot()[0]).isZero();
    }
    @Test void excludesOperationsStaticAndEventStreams() throws Exception {
        var telemetry=enabledTelemetry();var filter=new OperationsHttpFilter(telemetry);
        for(String path:new String[]{"/api/platform/operations/snapshot","/assets/logo.svg","/api/task/stream","/api/monitoring/events","/api/public/operations-health"}) filter.doFilter(new MockHttpServletRequest("GET",path),new MockHttpServletResponse(),(req,res)->{});
        var response=new MockHttpServletResponse();filter.doFilter(new MockHttpServletRequest("GET","/api/chat"),response,(req,res)->response.setContentType("text/event-stream"));
        verifyNoInteractions(telemetry);
    }
    @Test void cancellationDoesNotBecomeTechnicalFailure() {
        var telemetry=enabledTelemetry();var filter=new OperationsHttpFilter(telemetry);
        assertThatThrownBy(()->filter.doFilter(new MockHttpServletRequest("GET","/api/task"),new MockHttpServletResponse(),(req,res)->{throw new IOException("disconnected");})).isInstanceOf(IOException.class);
        var capture=ArgumentCaptor.forClass(OperationsTelemetry.HttpObservation.class);verify(telemetry).recordHttp(capture.capture());assertThat(capture.getValue().outcome()).isEqualTo("CANCELED");
    }
}
