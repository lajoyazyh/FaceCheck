package com.facecheck.admin.api;

import com.facecheck.admin.api.dto.SystemConfigItemResponse;
import com.facecheck.admin.api.dto.UpdateSystemConfigRequest;
import com.facecheck.admin.service.SystemConfigService;
import com.facecheck.auth.security.AdminOnly;
import com.facecheck.common.api.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@AdminOnly
@RequestMapping("/api/admin/system/config")
public class SystemConfigController {

    private final SystemConfigService systemConfigService;

    public SystemConfigController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @GetMapping
    public ApiResponse<List<SystemConfigItemResponse>> listConfigs() {
        return ApiResponse.success(systemConfigService.listConfigs());
    }

    @PutMapping("/{configKey}")
    public ApiResponse<SystemConfigItemResponse> updateConfig(
            @PathVariable String configKey,
            @Valid @RequestBody UpdateSystemConfigRequest request
    ) {
        return ApiResponse.success(systemConfigService.updateConfig(configKey, request));
    }
}
