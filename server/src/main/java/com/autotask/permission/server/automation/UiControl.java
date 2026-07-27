package com.autotask.permission.server.automation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
    name = "ui_controls",
    indexes = {
        @Index(name = "idx_ui_control_snapshot", columnList = "snapshot_id"),
        @Index(name = "idx_ui_control_key", columnList = "control_key")
    }
)
public class UiControl {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private UiSnapshot snapshot;

    @Column(name = "control_key", nullable = false, length = 120)
    private String controlKey;

    @Column(length = 500)
    private String text;

    @Column(name = "content_description", length = 500)
    private String contentDescription;

    @Column(name = "view_id", length = 260)
    private String viewId;

    @Column(name = "class_name", length = 240)
    private String className;

    @Column(name = "bounds_left")
    private Integer boundsLeft;

    @Column(name = "bounds_top")
    private Integer boundsTop;

    @Column(name = "bounds_right")
    private Integer boundsRight;

    @Column(name = "bounds_bottom")
    private Integer boundsBottom;

    private Integer depth;

    private boolean clickable;

    private boolean enabled;

    private boolean focusable;

    @Column(name = "visible_to_user")
    private boolean visibleToUser;

    public Long getId() {
        return id;
    }

    public UiSnapshot getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(UiSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public String getControlKey() {
        return controlKey;
    }

    public void setControlKey(String controlKey) {
        this.controlKey = controlKey;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public void setContentDescription(String contentDescription) {
        this.contentDescription = contentDescription;
    }

    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public Integer getBoundsLeft() {
        return boundsLeft;
    }

    public void setBoundsLeft(Integer boundsLeft) {
        this.boundsLeft = boundsLeft;
    }

    public Integer getBoundsTop() {
        return boundsTop;
    }

    public void setBoundsTop(Integer boundsTop) {
        this.boundsTop = boundsTop;
    }

    public Integer getBoundsRight() {
        return boundsRight;
    }

    public void setBoundsRight(Integer boundsRight) {
        this.boundsRight = boundsRight;
    }

    public Integer getBoundsBottom() {
        return boundsBottom;
    }

    public void setBoundsBottom(Integer boundsBottom) {
        this.boundsBottom = boundsBottom;
    }

    public Integer getDepth() {
        return depth;
    }

    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public boolean isClickable() {
        return clickable;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    public boolean isVisibleToUser() {
        return visibleToUser;
    }

    public void setVisibleToUser(boolean visibleToUser) {
        this.visibleToUser = visibleToUser;
    }
}
