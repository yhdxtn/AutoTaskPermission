package com.autotask.permission.server.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationPagePatternRepository extends JpaRepository<AutomationPagePattern, Long> {

    List<AutomationPagePattern> findByPackageNameOrderByUpdatedAtDesc(String packageName);

    void deleteByPackageName(String packageName);
}
