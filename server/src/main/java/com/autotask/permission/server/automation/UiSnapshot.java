package com.autotask.permission.server.automation;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "ui_snapshots",
    indexes = {
        @Index(name = "idx_ui_snapshot_package", columnList = "package_name"),
        @Index(name = "idx_ui_snapshot_captured_at", columnList = "captured_at")
    }
)
public class UiSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "package_name", nullable = false, length = 160)
    private String packageName;

    @Column(name = "app_name", length = 160)
    private String appName;

    @Column(name = "activity_name", length = 240)
    private String activityName;

    @Column(name = "window_title", length = 240)
    private String windowTitle;

    @Column(name = "device_id", length = 160)
    private String deviceId;

    @Column(name = "device_name", length = 160)
    private String deviceName;

    @Column(name = "screen_width")
    private Integer screenWidth;

    @Column(name = "screen_height")
    private Integer screenHeight;

    @Column(name = "captured_at", nullable = false)
    private LocalDateTime capturedAt;

    @OneToMany(mappedBy = "snapshot", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UiControl> controls = new ArrayList<>();

    @PrePersist
    void onCreate() {
        capturedAt = LocalDateTime.now();
    }

    public void addControl(UiControl control) {
        controls.add(control);
        control.setSnapshot(this);
    }

    public Long getId() {
        return id;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getActivityName() {
        return activityName;
    }

    public void setActivityName(String activityName) {
        this.activityName = activityName;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public Integer getScreenWidth() {
        return screenWidth;
    }

    public void setScreenWidth(Integer screenWidth) {
        this.screenWidth = screenWidth;
    }

    public Integer getScreenHeight() {
        return screenHeight;
    }

    public void setScreenHeight(Integer screenHeight) {
        this.screenHeight = screenHeight;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public List<UiControl> getControls() {
        return controls;
    }
}
