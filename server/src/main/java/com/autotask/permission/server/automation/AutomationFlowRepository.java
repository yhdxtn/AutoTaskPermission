package com.autotask.permission.server.automation;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationFlowRepository extends JpaRepository<AutomationFlow, Long> {

    List<AutomationFlow> findByPackageNameOrderByUpdatedAtDesc(String packageName);

    List<AutomationFlow> findByPackageNameAndEnabledTrueOrderByUpdatedAtDesc(String packageName);

    void deleteByPackageName(String packageName);
}
