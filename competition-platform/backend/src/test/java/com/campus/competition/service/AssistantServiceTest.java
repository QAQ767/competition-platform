package com.campus.competition.service;

import com.campus.competition.common.BusinessException;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.dto.TrainingAssistantReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantServiceTest {
    @Test void routesBasicRequestsWithoutModel() {
        assertEquals("SEARCH", AssistantService.fallbackMode("帮我找最近的高校竞赛", true));
        assertEquals("ADVICE", AssistantService.fallbackMode("按我的情况推荐比赛和队伍", false));
        assertEquals("LEARNING", AssistantService.fallbackMode("帮我制定比赛训练计划", true));
        assertEquals("DENIED", AssistantService.fallbackMode("联网找比赛", false));
    }
    @Test void modelCannotGrantStudentSearchAccess() {
        var llm = mock(LlmService.class);
        when(llm.generateText(anyString(), anyInt())).thenReturn(LlmService.LlmResult.ok("{\"mode\":\"SEARCH\",\"message\":\"找比赛\"}"));
        var service = new AssistantService(llm, new LlmProperties(), new ObjectMapper());
        var req = new TrainingAssistantReq(); req.setMessage("找比赛");
        assertEquals("DENIED", service.route(1L, req, false).get("mode"));
        assertThrows(BusinessException.class, () -> service.route(null, req, true));
    }
    @Test void rejectsUnsafeSourceLinksAndMissingSearchCredentials() {
        assertTrue(AssistantService.publicLink("https://www.example.edu.cn/notice"));
        assertFalse(AssistantService.publicLink("javascript:alert(1)"));
        assertFalse(AssistantService.publicLink("http://127.0.0.1/private"));
        assertFalse(AssistantService.publicLink("https://user:secret@example.com"));
        var service = new AssistantService(mock(LlmService.class), new LlmProperties(), new ObjectMapper());
        var req = new TrainingAssistantReq(); req.setMessage("找比赛");
        assertThrows(BusinessException.class, () -> service.search(1L, req));
    }
}
