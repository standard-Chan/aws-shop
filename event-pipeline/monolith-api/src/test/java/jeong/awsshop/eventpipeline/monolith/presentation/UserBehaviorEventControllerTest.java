package jeong.awsshop.eventpipeline.monolith.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jeong.awsshop.eventpipeline.common.UserBehaviorEventType;
import jeong.awsshop.eventpipeline.monolith.application.UserBehaviorEventService;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.EventAcceptedResponse;
import jeong.awsshop.eventpipeline.monolith.presentation.dto.SearchEventRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserBehaviorEventController.class)
class UserBehaviorEventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserBehaviorEventService userBehaviorEventService;

    @Test
    @DisplayName("검색 이벤트 요청을 받으면 202와 eventId를 반환해야 한다")
    void should_accept_search_event() throws Exception {
        when(userBehaviorEventService.recordSearch(any(SearchEventRequest.class)))
                .thenReturn(new EventAcceptedResponse(100L, UserBehaviorEventType.SEARCH));

        mockMvc.perform(post("/api/event-pipeline/events/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":1,"keyword":"macbook"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.eventId").value(100L))
                .andExpect(jsonPath("$.eventType").value("SEARCH"));

        verify(userBehaviorEventService).recordSearch(new SearchEventRequest(1L, "macbook"));
    }

    @Test
    @DisplayName("필수 값이 빠진 요청은 400을 반환해야 한다")
    void should_reject_invalid_event_request() throws Exception {
        mockMvc.perform(post("/api/event-pipeline/events/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"macbook"}
                                """))
                .andExpect(status().isBadRequest());
    }
}
