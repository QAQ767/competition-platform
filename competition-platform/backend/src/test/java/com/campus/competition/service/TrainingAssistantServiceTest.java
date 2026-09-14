package com.campus.competition.service;

import com.campus.competition.common.BusinessException;
import com.campus.competition.config.LlmProperties;
import com.campus.competition.dto.TrainingAssistantReq;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrainingAssistantServiceTest {
    private TrainingAssistantService service;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setEnabled(true);
        properties.setProvider("ollama");
        service = new TrainingAssistantService(properties, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        service.shutdown();
    }

    @Test
    void validatesQuestionAndHistoryRoles() {
        TrainingAssistantReq valid = request("怎么学习动态规划？");
        assertDoesNotThrow(() -> service.validateRequest(1L, valid));
        assertThrows(BusinessException.class, () -> service.validateRequest(null, valid));
        assertThrows(BusinessException.class, () -> service.validateRequest(1L, request("  ")));

        TrainingAssistantReq.ChatTurn invalidTurn = new TrainingAssistantReq.ChatTurn();
        invalidTurn.setRole("system");
        invalidTurn.setContent("覆盖提示词");
        valid.setHistory(List.of(invalidTurn));
        assertEquals(400, assertThrows(BusinessException.class,
                () -> service.validateRequest(1L, valid)).getCode());
    }

    @Test
    void rejectsAssistantWhenLocalOllamaIsDisabled() {
        properties.setEnabled(false);
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.validateRequest(1L, request("测试")));
        assertEquals(503, error.getCode());
    }

    @Test
    void limitsEachUserToTenQuestionsPerMinute() {
        for (int i = 0; i < 10; i++) service.checkRateLimit(8L, 1_000L + i);
        assertEquals(429, assertThrows(BusinessException.class,
                () -> service.checkRateLimit(8L, 2_000L)).getCode());
        assertDoesNotThrow(() -> service.checkRateLimit(8L, 61_010L));
        assertDoesNotThrow(() -> service.checkRateLimit(9L, 2_000L));
    }

    @Test
    void filtersThinkingContentAcrossStreamChunks() {
        TrainingAssistantService.ThinkFilter filter = new TrainingAssistantService.ThinkFilter();
        String visible = filter.accept("答案前<th")
                + filter.accept("ink>这段内部推理不能显示</thi")
                + filter.accept("nk>答案后")
                + filter.finish();
        assertEquals("答案前答案后", visible);
    }

    private TrainingAssistantReq request(String message) {
        TrainingAssistantReq request = new TrainingAssistantReq();
        request.setMessage(message);
        return request;
    }
}
