package com.campus.competition.controller;

import com.campus.competition.common.Result;
import com.campus.competition.common.UserContext;
import com.campus.competition.dto.TrainingAssistantReq;
import com.campus.competition.service.AssistantService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {
    private final AssistantService service;
    public AssistantController(AssistantService service) { this.service = service; }

    @PostMapping("/route")
    public Result<Map<String, String>> route(@Valid @RequestBody TrainingAssistantReq request, Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return Result.success(service.route(UserContext.getUserId(), request, admin));
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/search")
    public Result<Map<String, Object>> search(@Valid @RequestBody TrainingAssistantReq request) {
        return Result.success(service.search(UserContext.getUserId(), request));
    }
}
