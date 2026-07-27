package com.autotask.permission.server.automation;

import com.autotask.permission.server.automation.dto.AutomationDtos.FlowResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/device/automation")
public class DeviceAutomationFlowController {

    private final AutomationService service;

    public DeviceAutomationFlowController(AutomationService service) {
        this.service = service;
    }

    @GetMapping("/flows")
    List<FlowResponse> flows(@RequestParam String packageName) {
        return service.listEnabledFlows(packageName);
    }
}
