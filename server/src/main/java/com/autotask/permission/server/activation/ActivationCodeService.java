package com.autotask.permission.server.activation;

import com.autotask.permission.server.activation.dto.ActivationDtos.ActivationRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.ActivationResponse;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminBatchCreateRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminCodeResponse;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminCreateCodeRequest;
import com.autotask.permission.server.activation.dto.ActivationDtos.AdminUpdateCodeRequest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ActivationCodeService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();
    private final ActivationCodeRepository repository;

    public ActivationCodeService(ActivationCodeRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ActivationResponse activate(ActivationRequest request) {
        ActivationCode code = repository.findByCode(normalizeCode(request.code()))
            .orElse(null);
        if (code == null) {
            return failed("激活码不存在");
        }
        return bindOrVerify(code, normalizeDeviceId(request.deviceId()), trimToNull(request.deviceName()), true);
    }

    @Transactional
    public ActivationResponse verify(ActivationRequest request) {
        ActivationCode code = repository.findByCode(normalizeCode(request.code()))
            .orElse(null);
        if (code == null) {
            return failed("激活码不存在");
        }
        return bindOrVerify(code, normalizeDeviceId(request.deviceId()), trimToNull(request.deviceName()), false);
    }

    @Transactional(readOnly = true)
    public Page<AdminCodeResponse> list(String keyword, Pageable pageable) {
        String normalizedKeyword = trimToNull(keyword);
        Page<ActivationCode> page = normalizedKeyword == null
            ? repository.findAll(pageable)
            : repository.findByCodeContainingIgnoreCase(normalizedKeyword, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public AdminCodeResponse create(AdminCreateCodeRequest request) {
        ActivationCode entity = new ActivationCode();
        entity.setCode(resolveNewCode(request.code()));
        entity.setExpiresAt(request.expiresAt());
        entity.setRemark(trimToNull(request.remark()));
        return toResponse(repository.save(entity));
    }

    @Transactional
    public List<AdminCodeResponse> batchCreate(AdminBatchCreateRequest request) {
        int count = request.count() == null ? 10 : Math.min(request.count(), 500);
        LocalDateTime expiresAt = request.validDays() == null || request.validDays() <= 0
            ? null
            : LocalDateTime.now().plusDays(request.validDays());
        List<AdminCodeResponse> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ActivationCode entity = new ActivationCode();
            entity.setCode(resolveNewCode(null));
            entity.setExpiresAt(expiresAt);
            entity.setRemark(trimToNull(request.remark()));
            created.add(toResponse(repository.save(entity)));
        }
        return created;
    }

    @Transactional
    public AdminCodeResponse update(Long id, AdminUpdateCodeRequest request) {
        ActivationCode entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("激活码不存在"));
        if (request.state() != null) {
            entity.setState(request.state());
        }
        entity.setExpiresAt(request.expiresAt());
        entity.setRemark(trimToNull(request.remark()));
        if (Boolean.TRUE.equals(request.clearBinding())) {
            clearBinding(entity);
        }
        return toResponse(entity);
    }

    @Transactional
    public AdminCodeResponse enable(Long id) {
        ActivationCode entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("激活码不存在"));
        if (entity.getDeviceId() == null) {
            entity.setState(ActivationCodeState.UNUSED);
        } else {
            entity.setState(ActivationCodeState.BOUND);
        }
        return toResponse(entity);
    }

    @Transactional
    public AdminCodeResponse disable(Long id) {
        ActivationCode entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("激活码不存在"));
        entity.setState(ActivationCodeState.DISABLED);
        return toResponse(entity);
    }

    @Transactional
    public AdminCodeResponse unbind(Long id) {
        ActivationCode entity = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("激活码不存在"));
        clearBinding(entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        repository.deleteById(id);
    }

    private ActivationResponse bindOrVerify(
        ActivationCode code,
        String deviceId,
        String deviceName,
        boolean allowBind
    ) {
        if (code.getState() == ActivationCodeState.DISABLED) {
            return failed("激活码已被禁用");
        }
        if (code.isExpired()) {
            return failed("激活码已过期");
        }
        if (code.getState() == ActivationCodeState.UNUSED) {
            if (!allowBind) {
                return failed("激活码尚未绑定，请先激活");
            }
            code.setState(ActivationCodeState.BOUND);
            code.setDeviceId(deviceId);
            code.setDeviceName(deviceName);
            code.setBoundAt(LocalDateTime.now());
            code.setLastVerifiedAt(LocalDateTime.now());
            return success(code, "激活成功，已绑定当前设备");
        }
        if (!deviceId.equals(code.getDeviceId())) {
            return failed("激活码已绑定其他设备");
        }
        code.setDeviceName(deviceName == null ? code.getDeviceName() : deviceName);
        code.setLastVerifiedAt(LocalDateTime.now());
        return success(code, "验证成功");
    }

    private void clearBinding(ActivationCode entity) {
        entity.setState(ActivationCodeState.UNUSED);
        entity.setDeviceId(null);
        entity.setDeviceName(null);
        entity.setBoundAt(null);
        entity.setLastVerifiedAt(null);
    }

    private String resolveNewCode(String requestedCode) {
        String code = normalizeCode(requestedCode);
        if (code != null) {
            if (repository.existsByCode(code)) {
                throw new IllegalArgumentException("激活码已存在");
            }
            return code;
        }
        do {
            code = generateCode();
        } while (repository.existsByCode(code));
        return code;
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder();
        for (int group = 0; group < 4; group++) {
            if (group > 0) {
                builder.append('-');
            }
            for (int i = 0; i < 4; i++) {
                builder.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
            }
        }
        return builder.toString();
    }

    private AdminCodeResponse toResponse(ActivationCode entity) {
        return new AdminCodeResponse(
            entity.getId(),
            entity.getCode(),
            entity.getState(),
            entity.isExpired(),
            entity.getDeviceId(),
            entity.getDeviceName(),
            entity.getBoundAt(),
            entity.getLastVerifiedAt(),
            entity.getExpiresAt(),
            entity.getRemark(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ActivationResponse success(ActivationCode code, String message) {
        return new ActivationResponse(true, message, code.getCode(), code.getDeviceId(), code.getExpiresAt());
    }

    private ActivationResponse failed(String message) {
        return new ActivationResponse(false, message, null, null, null);
    }

    private String normalizeCode(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String normalizeDeviceId(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("设备标识不能为空");
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
