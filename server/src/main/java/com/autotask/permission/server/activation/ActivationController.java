package com.autotask.permission.server.activation;

import com.autotask.permission.server.activation.dto.ActivationDtos.ActivationRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.ActivationResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/activation")
public class ActivationController {

    private final ActivationCodeService service;

    public ActivationController(ActivationCodeService service) {
        this.service = service;
    }

    @PostMapping("/activate")
    ResponseEntity<ActivationResponse> activate(@Valid @RequestBody ActivationRequest request) {
        ActivationResponse response = service.activate(request);
        return response.success() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }

    @PostMapping("/verify")
    ResponseEntity<ActivationResponse> verify(@Valid @RequestBody ActivationRequest request) {
        ActivationResponse response = service.verify(request);
        return response.success() ? ResponseEntity.ok(response) : ResponseEntity.badRequest().body(response);
    }
}
