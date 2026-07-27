package com.autotask.permission.server.automation;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UiSnapshotRepository extends JpaRepository<UiSnapshot, Long> {

    @EntityGraph(attributePaths = "controls")
    Optional<UiSnapshot> findFirstByPackageNameOrderByCapturedAtDesc(String packageName);

    List<UiSnapshot> findAllByOrderByCapturedAtDesc();

    List<UiSnapshot> findByPackageNameOrderByCapturedAtDesc(String packageName);

    @Query("select count(c) from UiControl c where c.snapshot.id = :snapshotId")
    long countControlsBySnapshotId(@Param("snapshotId") Long snapshotId);
}
