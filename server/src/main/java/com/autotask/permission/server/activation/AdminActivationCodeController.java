package com.autotask.permission.server.activation;

import com.autotask.permission.server.activation.dto.ActivationDtos.AdminBatchCreateRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminCodeResponse;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminCreateCodeRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminUpdateCodeRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/codes")
public class AdminActivationCodeController {

    private final ActivationCodeService service;

    public AdminActivationCodeController(ActivationCodeService service) {
        this.service = service;
    }

    @GetMapping
    Page<AdminCodeResponse> list(
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        int pageSize = Math.max(1, Math.min(size, 100));
        return service.list(
            keyword,
            PageRequest.of(Math.max(0, page), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
    }

    @PostMapping
    AdminCodeResponse create(@RequestBody AdminCreateCodeRequest request) {
        return service.create(request);
    }

    @PostMapping("/batch")
    List<AdminCodeResponse> batchCreate(@Valid @RequestBody AdminBatchCreateRequest request) {
        return service.batchCreate(request);
    }

    @PatchMapping("/{id}")
    AdminCodeResponse update(@PathVariable Long id, @RequestBody AdminUpdateCodeRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/enable")
    AdminCodeResponse enable(@PathVariable Long id) {
        return service.enable(id);
    }

    @PostMapping("/{id}/disable")
    AdminCodeResponse disable(@PathVariable Long id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/unbind")
    AdminCodeResponse unbind(@PathVariable Long id) {
        return service.unbind(id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ActivationError> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(new ActivationError(ex.getMessage()));
    }

    record ActivationError(String message) {
    }
}
