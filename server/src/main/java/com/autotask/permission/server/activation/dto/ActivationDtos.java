package com.autotask.permission.server.activation.dto;

import com.autotask.permission.server.activation.ActivationCodeState;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public final class ActivationDtos {

    private ActivationDtos() {
    }

    public record ActivationRequest(
        @NotBlank String code,
        @NotBlank String deviceId,
        String deviceName
    ) {
    }

    public record ActivationResponse(
        boolean success,
        String message,
        String code,
        String deviceId,
        LocalDateTime expiresAt
    ) {
    }

    public record AdminCreateCodeRequest(
        String code,
        LocalDateTime expiresAt,
        String remark
    ) {
    }

    public record AdminBatchCreateRequest(
        @Positive Integer count,
        Integer validDays,
        String remark
    ) {
    }

    public record AdminUpdateCodeRequest(
        ActivationCodeState state,
        LocalDateTime expiresAt,
        String remark,
        Boolean clearBinding
    ) {
    }

    public record AdminCodeResponse(
        @NotNull Long id,
        String code,
        ActivationCodeState state,
        boolean expired,
        String deviceId,
        String deviceName,
        LocalDateTime boundAt,
        LocalDateTime lastVerifiedAt,
        LocalDateTime expiresAt,
        String remark,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }
}
