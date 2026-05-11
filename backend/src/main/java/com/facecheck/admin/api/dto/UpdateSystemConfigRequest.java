package com.facecheck.admin.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateSystemConfigRequest(@NotBlank String configValue) {
}
