package com.autotask.permission.server.automation;

import com.autotask.permission.server.automation.dto.AutomationDtos.AppSummaryResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.AppRemarkRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.ControlResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.ControlUploadRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.FlowRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.FlowResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.PagePatternRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.PagePatternResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotSummaryResponse;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotUploadRequest;
import com.autotask.permission.server.automation.dto.AutomationDtos.SnapshotUploadResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AutomationService {

    private static final int MAX_CONTROLS_PER_SNAPSHOT = 240;

    private final UiSnapshotRepository snapshotRepository;
    private final AutomationFlowRepository flowRepository;
    private final AutomationAppProfileRepository appProfileRepository;
    private final AutomationPagePatternRepository pagePatternRepository;
    private final ObjectMapper objectMapper;

    public AutomationService(
        UiSnapshotRepository snapshotRepository,
        AutomationFlowRepository flowRepository,
        AutomationAppProfileRepository appProfileRepository,
        AutomationPagePatternRepository pagePatternRepository,
        ObjectMapper objectMapper
    ) {
        this.snapshotRepository = snapshotRepository;
        this.flowRepository = flowRepository;
        this.appProfileRepository = appProfileRepository;
        this.pagePatternRepository = pagePatternRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public SnapshotUploadResponse saveSnapshot(SnapshotUploadRequest request) {
        UiSnapshot snapshot = new UiSnapshot();
        snapshot.setPackageName(requiredTrim(request.packageName(), "应用包名不能为空"));
        snapshot.setAppName(trimToNull(request.appName()));
        snapshot.setActivityName(trimToNull(request.activityName()));
        snapshot.setWindowTitle(trimToNull(request.windowTitle()));
        snapshot.setDeviceId(trimToNull(request.deviceId()));
        snapshot.setDeviceName(trimToNull(request.deviceName()));
        snapshot.setScreenWidth(request.screenWidth());
        snapshot.setScreenHeight(request.screenHeight());

        List<ControlUploadRequest> controls = request.controls() == null ? List.of() : request.controls();
        int count = Math.min(controls.size(), MAX_CONTROLS_PER_SNAPSHOT);
        for (int i = 0; i < count; i++) {
            snapshot.addControl(toEntity(controls.get(i), i));
        }

        UiSnapshot saved = snapshotRepository.save(snapshot);
        return new SnapshotUploadResponse(saved.getId(), saved.getControls().size(), saved.getCapturedAt());
    }

    @Transactional(readOnly = true)
    public List<AppSummaryResponse> listApps() {
        Map<String, UiSnapshot> latestByPackage = new LinkedHashMap<>();
        for (UiSnapshot snapshot : snapshotRepository.findAllByOrderByCapturedAtDesc()) {
            latestByPackage.putIfAbsent(snapshot.getPackageName(), snapshot);
        }
        List<AppSummaryResponse> responses = new ArrayList<>();
        for (UiSnapshot snapshot : latestByPackage.values()) {
            AutomationAppProfile profile = appProfileRepository.findById(snapshot.getPackageName()).orElse(null);
            responses.add(new AppSummaryResponse(
                snapshot.getPackageName(),
                snapshot.getAppName(),
                profile == null ? null : profile.getRemark(),
                snapshot.getActivityName(),
                snapshot.getDeviceName(),
                snapshot.getScreenWidth(),
                snapshot.getScreenHeight(),
                snapshot.getCapturedAt(),
                (int) snapshotRepository.countControlsBySnapshotId(snapshot.getId())
            ));
        }
        return responses;
    }

    @Transactional
    public AppSummaryResponse updateAppRemark(String packageName, AppRemarkRequest request) {
        String normalizedPackageName = requiredTrim(packageName, "应用包名不能为空");
        AutomationAppProfile profile = appProfileRepository.findById(normalizedPackageName)
            .orElseGet(() -> {
                AutomationAppProfile created = new AutomationAppProfile();
                created.setPackageName(normalizedPackageName);
                return created;
            });
        profile.setRemark(trimToNull(request.remark()));
        appProfileRepository.save(profile);
        UiSnapshot snapshot = snapshotRepository.findFirstByPackageNameOrderByCapturedAtDesc(normalizedPackageName)
            .orElseThrow(() -> new IllegalArgumentException("还没有采集到这个 App"));
        return new AppSummaryResponse(
            snapshot.getPackageName(),
            snapshot.getAppName(),
            profile.getRemark(),
            snapshot.getActivityName(),
            snapshot.getDeviceName(),
            snapshot.getScreenWidth(),
            snapshot.getScreenHeight(),
            snapshot.getCapturedAt(),
            (int) snapshotRepository.countControlsBySnapshotId(snapshot.getId())
        );
    }

    @Transactional(readOnly = true)
    public SnapshotResponse latestSnapshot(String packageName) {
        UiSnapshot snapshot = snapshotRepository.findFirstByPackageNameOrderByCapturedAtDesc(packageName)
            .orElseThrow(() -> new IllegalArgumentException("还没有采集到这个 App 的控件"));
        return toResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public List<SnapshotSummaryResponse> listSnapshots(String packageName) {
        return snapshotRepository.findByPackageNameOrderByCapturedAtDesc(packageName)
            .stream()
            .map(this::toSummaryResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public SnapshotResponse getSnapshot(Long id) {
        UiSnapshot snapshot = snapshotRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("页面快照不存在"));
        return toResponse(snapshot);
    }

    @Transactional(readOnly = true)
    public List<FlowResponse> listFlows(String packageName) {
        return flowRepository.findByPackageNameOrderByUpdatedAtDesc(packageName)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<FlowResponse> listEnabledFlows(String packageName) {
        return flowRepository.findByPackageNameAndEnabledTrueOrderByUpdatedAtDesc(packageName)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public FlowResponse createFlow(FlowRequest request) {
        AutomationFlow flow = new AutomationFlow();
        applyFlowRequest(flow, request);
        return toResponse(flowRepository.save(flow));
    }

    @Transactional
    public FlowResponse updateFlow(Long id, FlowRequest request) {
        AutomationFlow flow = flowRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("流程不存在"));
        applyFlowRequest(flow, request);
        return toResponse(flow);
    }

    @Transactional
    public void deleteFlow(Long id) {
        flowRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<PagePatternResponse> listPagePatterns(String packageName) {
        return pagePatternRepository.findByPackageNameOrderByUpdatedAtDesc(packageName)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public PagePatternResponse createPagePattern(PagePatternRequest request) {
        AutomationPagePattern pattern = new AutomationPagePattern();
        pattern.setPackageName(requiredTrim(request.packageName(), "应用包名不能为空"));
        pattern.setName(requiredTrim(request.name(), "页面名称不能为空"));
        pattern.setDescription(trimToNull(request.description()));
        pattern.setSnapshotId(request.snapshotId());
        pattern.setActivityName(trimToNull(request.activityName()));
        pattern.setRequiredControlsJson(writeJson(request.requiredControls()));
        return toResponse(pagePatternRepository.save(pattern));
    }

    @Transactional
    public void deletePagePattern(Long id) {
        pagePatternRepository.deleteById(id);
    }

    private UiControl toEntity(ControlUploadRequest request, int index) {
        UiControl control = new UiControl();
        control.setControlKey(StringUtils.hasText(request.key()) ? request.key().trim() : "node-" + index);
        control.setText(trimToNull(request.text()));
        control.setContentDescription(trimToNull(request.contentDescription()));
        control.setViewId(trimToNull(request.viewId()));
        control.setClassName(trimToNull(request.className()));
        control.setBoundsLeft(request.left());
        control.setBoundsTop(request.top());
        control.setBoundsRight(request.right());
        control.setBoundsBottom(request.bottom());
        control.setDepth(request.depth());
        control.setClickable(Boolean.TRUE.equals(request.clickable()));
        control.setEnabled(Boolean.TRUE.equals(request.enabled()));
        control.setFocusable(Boolean.TRUE.equals(request.focusable()));
        control.setVisibleToUser(Boolean.TRUE.equals(request.visibleToUser()));
        return control;
    }

    private void applyFlowRequest(AutomationFlow flow, FlowRequest request) {
        flow.setPackageName(requiredTrim(request.packageName(), "应用包名不能为空"));
        flow.setName(requiredTrim(request.name(), "流程名称不能为空"));
        flow.setDescription(trimToNull(request.description()));
        flow.setEnabled(!Boolean.FALSE.equals(request.enabled()));
        flow.setNodesJson(writeJson(request.nodes()));
        flow.setEdgesJson(writeJson(request.edges()));
    }

    private SnapshotResponse toResponse(UiSnapshot snapshot) {
        return new SnapshotResponse(
            snapshot.getId(),
            snapshot.getPackageName(),
            snapshot.getAppName(),
            snapshot.getActivityName(),
            snapshot.getWindowTitle(),
            snapshot.getDeviceId(),
            snapshot.getDeviceName(),
            snapshot.getScreenWidth(),
            snapshot.getScreenHeight(),
            snapshot.getCapturedAt(),
            snapshot.getControls().stream().map(control -> toResponse(control, snapshot)).toList()
        );
    }

    private SnapshotSummaryResponse toSummaryResponse(UiSnapshot snapshot) {
        return new SnapshotSummaryResponse(
            snapshot.getId(),
            snapshot.getPackageName(),
            snapshot.getAppName(),
            snapshot.getActivityName(),
            snapshot.getWindowTitle(),
            snapshot.getDeviceName(),
            snapshot.getScreenWidth(),
            snapshot.getScreenHeight(),
            snapshot.getCapturedAt(),
            (int) snapshotRepository.countControlsBySnapshotId(snapshot.getId())
        );
    }

    private ControlResponse toResponse(UiControl control, UiSnapshot snapshot) {
        int screenWidth = positiveOrFallback(snapshot.getScreenWidth(), control.getBoundsRight());
        int screenHeight = positiveOrFallback(snapshot.getScreenHeight(), control.getBoundsBottom());
        return new ControlResponse(
            control.getId(),
            control.getControlKey(),
            control.getText(),
            control.getContentDescription(),
            control.getViewId(),
            control.getClassName(),
            control.getBoundsLeft(),
            control.getBoundsTop(),
            control.getBoundsRight(),
            control.getBoundsBottom(),
            ratio(control.getBoundsLeft(), screenWidth),
            ratio(control.getBoundsTop(), screenHeight),
            ratio(control.getBoundsRight(), screenWidth),
            ratio(control.getBoundsBottom(), screenHeight),
            centerRatio(control.getBoundsLeft(), control.getBoundsRight(), screenWidth),
            centerRatio(control.getBoundsTop(), control.getBoundsBottom(), screenHeight),
            control.getDepth(),
            control.isClickable(),
            control.isEnabled(),
            control.isFocusable(),
            control.isVisibleToUser()
        );
    }

    private FlowResponse toResponse(AutomationFlow flow) {
        return new FlowResponse(
            flow.getId(),
            flow.getPackageName(),
            flow.getName(),
            flow.getDescription(),
            flow.isEnabled(),
            readJson(flow.getNodesJson()),
            readJson(flow.getEdgesJson()),
            flow.getCreatedAt(),
            flow.getUpdatedAt()
        );
    }

    private PagePatternResponse toResponse(AutomationPagePattern pattern) {
        return new PagePatternResponse(
            pattern.getId(),
            pattern.getPackageName(),
            pattern.getName(),
            pattern.getDescription(),
            pattern.getSnapshotId(),
            pattern.getActivityName(),
            readJson(pattern.getRequiredControlsJson()),
            pattern.getCreatedAt(),
            pattern.getUpdatedAt()
        );
    }

    private String writeJson(JsonNode node) {
        try {
            JsonNode value = node == null ? objectMapper.createArrayNode() : node;
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("流程图数据格式不正确");
        }
    }

    private JsonNode readJson(String json) {
        try {
            return StringUtils.hasText(json) ? objectMapper.readTree(json) : objectMapper.createArrayNode();
        } catch (JsonProcessingException ex) {
            return objectMapper.createArrayNode();
        }
    }

    private int positiveOrFallback(Integer value, Integer fallback) {
        if (value != null && value > 0) {
            return value;
        }
        if (fallback != null && fallback > 0) {
            return fallback;
        }
        return 1;
    }

    private Double ratio(Integer value, int base) {
        if (value == null || base <= 0) {
            return null;
        }
        return Math.round((value.doubleValue() / base) * 10000d) / 10000d;
    }

    private Double centerRatio(Integer start, Integer end, int base) {
        if (start == null || end == null || base <= 0) {
            return null;
        }
        return Math.round((((start + end) / 2d) / base) * 10000d) / 10000d;
    }

    private String requiredTrim(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
