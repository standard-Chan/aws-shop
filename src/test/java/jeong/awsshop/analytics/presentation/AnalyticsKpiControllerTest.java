package jeong.awsshop.analytics.presentation;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import jeong.awsshop.analytics.application.AnalyticsKpiService;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiItemResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKeywordKpiResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsKpiSummaryResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiItemResponse;
import jeong.awsshop.analytics.presentation.dto.AnalyticsProductKpiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@WebMvcTest(AnalyticsKpiController.class)
class AnalyticsKpiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AnalyticsKpiService analyticsKpiService;

    @Test
    @DisplayName("Summary KPI 요청이 정상적이면 200과 KPI 응답을 반환해야 한다")
    void should_return_summary_kpi() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsKpiService.getSummary(from, to))
                .thenReturn(new AnalyticsKpiSummaryResponse(
                        from, to, "EVENT_COUNT", 100L, 40L, 12L, 3L, 0.4, 0.3, 0.075
                ));

        mockMvc.perform(get("/api/analytics/kpis/summary")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("EVENT_COUNT"))
                .andExpect(jsonPath("$.searchCount").value(100L))
                .andExpect(jsonPath("$.searchCtr").value(0.4))
                .andExpect(jsonPath("$.purchaseRate").value(0.075));
    }

    @Test
    @DisplayName("Product KPI 요청이 정상적이면 200과 상품별 KPI 응답을 반환해야 한다")
    void should_return_product_kpi() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsKpiService.getProducts(from, to, 20))
                .thenReturn(new AnalyticsProductKpiResponse(
                        from,
                        to,
                        "EVENT_COUNT",
                        List.of(new AnalyticsProductKpiItemResponse(100L, 40L, 12L, 0.3, null))
                ));

        mockMvc.perform(get("/api/analytics/kpis/products")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].productId").value(100L))
                .andExpect(jsonPath("$.items[0].cartRate").value(0.3))
                .andExpect(jsonPath("$.items[0].purchaseRate").doesNotExist());
    }

    @Test
    @DisplayName("Keyword KPI 요청이 정상적이면 200과 검색어별 KPI 응답을 반환해야 한다")
    void should_return_keyword_kpi() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsKpiService.getKeywords(from, to, 20))
                .thenReturn(new AnalyticsKeywordKpiResponse(
                        from,
                        to,
                        "EVENT_COUNT",
                        List.of(new AnalyticsKeywordKpiItemResponse("macbook", 100L, 30L, 0.3))
                ));

        mockMvc.perform(get("/api/analytics/kpis/keywords")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].keyword").value("macbook"))
                .andExpect(jsonPath("$.items[0].searchCtr").value(0.3));
    }

    @Test
    @DisplayName("from 또는 to가 없거나 형식이 잘못되면 400을 반환해야 한다")
    void should_return_bad_request_when_period_is_missing_or_invalid() throws Exception {
        mockMvc.perform(get("/api/analytics/kpis/summary")
                        .param("from", "2026-05-01T00:00:00Z"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/analytics/kpis/summary")
                        .param("from", "2026-05-01")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("from이 to보다 빠르지 않으면 400을 반환해야 한다")
    void should_return_bad_request_when_period_is_invalid() throws Exception {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsKpiService.getSummary(from, to))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must be before to"));

        mockMvc.perform(get("/api/analytics/kpis/summary")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("limit이 1에서 100 사이가 아니면 400을 반환해야 한다")
    void should_return_bad_request_when_limit_is_invalid() throws Exception {
        Instant from = Instant.parse("2026-05-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-01T00:00:00Z");
        when(analyticsKpiService.getProducts(from, to, 101))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 100"));

        mockMvc.perform(get("/api/analytics/kpis/products")
                        .param("from", "2026-05-01T00:00:00Z")
                        .param("to", "2026-06-01T00:00:00Z")
                        .param("limit", "101"))
                .andExpect(status().isBadRequest());
    }
}
