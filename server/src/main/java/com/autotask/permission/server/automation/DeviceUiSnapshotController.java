package com.autotask.permission.server.automation;

import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotUploadRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotUploadResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device/ui-snapshots")
public class DeviceUiSnapshotController {

    private final AutomationService service;

    public DeviceUiSnapshotController(AutomationService service) {
        this.service = service;
    }

    @PostMapping
    SnapshotUploadResponse upload(@Valid @RequestBody SnapshotUploadRequest request) {
        return service.saveSnapshot(request);
    }
}
