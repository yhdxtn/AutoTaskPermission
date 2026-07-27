package com.autotask.permission.server.activation;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface ActivationCodeRepository extends JpaRepository<ActivationCode, Long> {

    boolean existsByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ActivationCode> findByCode(String code);

    Page<ActivationCode> findByCodeContainingIgnoreCase(String keyword, Pageable pageable);
}
