package jeong.awsshop.analytics.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.analytics.application.AnalyticsFunnelService;
import jeong.awsshop.analytics.domain.AnalyticsEventType;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsFunnelStepResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsFunnelController.class)
class AnalyticsFunnelControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsFunnelService analyticsFunnelService;

    @Test
    @DisplayName("퍼널 분석 요청이 정상적이면 200과 단계별 count/전환율을 반환해야 한다")
    void should_return_funnel() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsFunnelService.getFunnel(from, to))
                .thenReturn(new AnalyticsFunnelResponse(
                        from,
                        to,
                        "EVENT_COUNT",
                        List.of(
                                new AnalyticsFunnelStepResponse(AnalyticsEventType.SEARCH, 100L, null, 1.0),
                                new AnalyticsFunnelStepResponse(AnalyticsEventType.PRODUCT_VIEW, 40L, 0.4, 0.4),
                                new AnalyticsFunnelStepResponse(AnalyticsEventType.ADD_TO_CART, 12L, 0.3, 0.12),
                                new AnalyticsFunnelStepResponse(AnalyticsEventType.PURCHASE, 3L, 0.25, 0.03)
                        )
                ));

        mockMvc.perform(get("/api/analytics/funnel")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2026-05-01T00:00:00Z"))
                .andExpect(jsonPath("$.to").value("2026-06-01T00:00:00Z"))
                .andExpect(jsonPath("$.basis").value("EVENT_COUNT"))
                .andExpect(jsonPath("$.steps[0].eventType").value("SEARCH"))
                .andExpect(jsonPath("$.steps[0].count").value(100L))
                .andExpect(jsonPath("$.steps[0].conversionRateFromPrevious").doesNotExist())
                .andExpect(jsonPath("$.steps[0].conversionRateFromSearch").value(1.0))
                .andExpect(jsonPath("$.steps[3].eventType").value("PURCHASE"))
                .andExpect(jsonPath("$.steps[3].conversionRateFromPrevious").value(0.25));
    }

    @Test
    @DisplayName("from 또는 to가 없으면 400을 반환해야 한다")
    void should_return_bad_request_when_period_is_missing() throws Exception {
        mockMvc.perform(get("/api/analytics/funnel")
                        .param("from", "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("from 또는 to 형식이 Instant가 아니면 400을 반환해야 한다")
    void should_return_bad_request_when_period_format_is_invalid() throws Exception {
        mockMvc.perform(get("/api/analytics/funnel")
                        .param("from", "2026-05-01")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("from이 to보다 빠르지 않으면 400을 반환해야 한다")
    void should_return_bad_request_when_period_is_invalid() throws Exception {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsFunnelService.getFunnel(from, to))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to"));

        mockMvc.perform(get("/api/analytics/funnel")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }
}
