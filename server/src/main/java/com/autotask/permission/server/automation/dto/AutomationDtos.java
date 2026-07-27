package com.autotask.permission.server.automation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;

public final class AutomationDtos {

    private AutomationDtos() {
    }

    public record SnapshotUploadRequest(
        @NotBlank String packageName,
        String appName,
        String activityName,
        String windowTitle,
        String deviceId,
        String deviceName,
        Integer screenWidth,
        Integer screenHeight,
        @Valid List<ControlUploadRequest> controls
    ) {
    }

    public record ControlUploadRequest(
        String key,
        String text,
        String contentDescription,
        String viewId,
        String className,
        Integer left,
        Integer top,
        Integer right,
        Integer bottom,
        Integer depth,
        Boolean clickable,
        Boolean enabled,
        Boolean focusable,
        Boolean visibleToUser
    ) {
    }

    public record SnapshotUploadResponse(
        Long id,
        int controlCount,
        LocalDateTime capturedAt
    ) {
    }

    public record AppSummaryResponse(
        String packageName,
        String appName,
        String remark,
        String activityName,
        String deviceName,
        Integer screenWidth,
        Integer screenHeight,
        LocalDateTime capturedAt,
        int controlCount
    ) {
    }

    public record AppRemarkRequest(
        String remark
    ) {
    }

    public record SnapshotResponse(
        Long id,
        String packageName,
        String appName,
        String activityName,
        String windowTitle,
        String deviceId,
        String deviceName,
        Integer screenWidth,
        Integer screenHeight,
        LocalDateTime capturedAt,
        List<ControlResponse> controls
    ) {
    }

    public record SnapshotSummaryResponse(
        Long id,
        String packageName,
        String appName,
        String activityName,
        String windowTitle,
        String deviceName,
        Integer screenWidth,
        Integer screenHeight,
        LocalDateTime capturedAt,
        int controlCount
    ) {
    }

    public record ControlResponse(
        Long id,
        String key,
        String text,
        String contentDescription,
        String viewId,
        String className,
        Integer left,
        Integer top,
        Integer right,
        Integer bottom,
        Double leftRatio,
        Double topRatio,
        Double rightRatio,
        Double bottomRatio,
        Double centerXRatio,
        Double centerYRatio,
        Integer depth,
        boolean clickable,
        boolean enabled,
        boolean focusable,
        boolean visibleToUser
    ) {
    }

    public record FlowRequest(
        @NotBlank String packageName,
        @NotBlank String name,
        String description,
        Boolean enabled,
        JsonNode nodes,
        JsonNode edges
    ) {
    }

    public record FlowResponse(
        Long id,
        String packageName,
        String name,
        String description,
        boolean enabled,
        JsonNode nodes,
        JsonNode edges,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }

    public record PagePatternRequest(
        @NotBlank String packageName,
        @NotBlank String name,
        String description,
        Long snapshotId,
        String activityName,
        JsonNode requiredControls
    ) {
    }

    public record PagePatternResponse(
        Long id,
        String packageName,
        String name,
        String description,
        Long snapshotId,
        String activityName,
        JsonNode requiredControls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
    }
}
