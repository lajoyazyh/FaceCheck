package com.facecheck.admin.api.dto;

import jakarta.validation.constraints.Size;

public record ReviewCheckinAttemptRequest(
        @Size(max = 500) String note,
        Boolean reviewed
) {
}
