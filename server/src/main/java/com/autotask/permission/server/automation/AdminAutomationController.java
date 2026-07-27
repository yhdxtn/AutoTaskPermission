package com.autotask.permission.server.automation;

import com.autotask.permission.server.automation.dto.AutomationDtos.AppSummaryResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.AppRemarkRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.FlowRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.FlowResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotSummaryResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/automation")
public class AdminAutomationController {

    private final AutomationService service;

    public AdminAutomationController(AutomationService service) {
        this.service = service;
    }

    @GetMapping("/apps")
    List<AppSummaryResponse> apps() {
        return service.listApps();
    }

    @PutMapping("/apps/{packageName}/remark")
    AppSummaryResponse updateAppRemark(@PathVariable String packageName, @RequestBody AppRemarkRequest request) {
        return service.updateAppRemark(packageName, request);
    }

    @GetMapping("/snapshots/latest")
    SnapshotResponse latestSnapshot(@RequestParam String packageName) {
        return service.latestSnapshot(packageName);
    }

    @GetMapping("/snapshots")
    List<SnapshotSummaryResponse> snapshots(@RequestParam String packageName) {
        return service.listSnapshots(packageName);
    }

    @GetMapping("/snapshots/{id}")
    SnapshotResponse snapshot(@PathVariable Long id) {
        return service.getSnapshot(id);
    }

    @GetMapping("/flows")
    List<FlowResponse> flows(@RequestParam String packageName) {
        return service.listFlows(packageName);
    }

    @PostMapping("/flows")
    FlowResponse createFlow(@Valid @RequestBody FlowRequest request) {
        return service.createFlow(request);
    }

    @PutMapping("/flows/{id}")
    FlowResponse updateFlow(@PathVariable Long id, @Valid @RequestBody FlowRequest request) {
        return service.updateFlow(id, request);
    }

    @DeleteMapping("/flows/{id}")
    ResponseEntity<Void> deleteFlow(@PathVariable Long id) {
        service.deleteFlow(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<AutomationError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new AutomationError(ex.getMessage()));
    }

    record AutomationError(String message) {
    }
}
