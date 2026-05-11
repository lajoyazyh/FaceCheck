package com.facecheck.admin.api;

import com.facecheck.admin.api.dto.SystemStateResponse;
import com.facecheck.admin.service.SystemStateService;
import com.facecheck.auth.security.AdminOnly;
import com.facecheck.common.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin/system/state")
public class SystemStateController {

    private final SystemStateService systemStateService;

    public SystemStateController(SystemStateService systemStateService) {
        this.systemStateService = systemStateService;
    }

    @GetMapping
    public ApiResponse<SystemStateResponse> currentState() {
        return ApiResponse.success(systemStateService.currentState());
    }
}
