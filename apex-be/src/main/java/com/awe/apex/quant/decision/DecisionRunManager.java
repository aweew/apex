package com.awe.apex.quant.decision;

import com.awe.apex.common.util.JsonUtils;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.quant.domain.entity.DecisionFeatureSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DecisionFeatureSnapshotMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class DecisionRunManager {

    public static final String RULE_VERSION = "RULE_V1";
    public static final String MODEL_VERSION = "RULE_CHAMPION_V1";
    public static final String FEATURE_VERSION = "FEATURE_V1";
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private DecisionRunMapper decisionRunMapper;

    @Resource
    private DecisionFeatureSnapshotMapper featureSnapshotMapper;

    /**
     * 创建决策运行
     *
     * @param context   决策上下文
     * @param groupName 自选分组
     * @return 决策运行
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DecisionRun start(DecisionContext context, String groupName) {
        return start(context, groupName, Map.of());
    }

    /**
     * 创建包含冻结配置的决策运行
     *
     * @param context        决策上下文
     * @param groupName      自选分组
     * @param configSnapshot 冻结配置
     * @return 决策运行
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DecisionRun start(DecisionContext context, String groupName, Map<String, Object> configSnapshot) {
        LocalDateTime now = LocalDateTime.now();
        DecisionRun run = DecisionRun.builder()
                .runNo(createRunNo(now))
                .actionDate(context.getActionDate())
                .asOfTime(context.getAsOfTime())
                .groupName(groupName)
                .mode(context.getMode().name())
                .ruleVersion(RULE_VERSION)
                .modelVersion(MODEL_VERSION)
                .featureVersion(FEATURE_VERSION)
                .dataCutoffJson(dataCutoffJson(context))
                .configSnapshotJson(toJson(configSnapshot))
                .status("RUNNING")
                .startedAt(now)
                .published(0)
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
        if (decisionRunMapper.insert(run) != 1) {
            throw new BusinessException("决策运行创建失败，actionDate=" + context.getActionDate());
        }
        return run;
    }

    /**
     * 保存本次运行的全候选特征快照
     *
     * @param run      决策运行
     * @param features 特征列表
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFeatures(DecisionRun run, List<DecisionFeature> features) {
        if (Objects.isNull(run) || Objects.isNull(run.getId()) || Objects.isNull(features)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DecisionFeature feature : features) {
            if (featureSnapshotMapper.insert(toSnapshot(run, feature, now)) != 1) {
                throw new BusinessException("决策特征快照保存失败，code=" + feature.getCode()
                        + "，action=" + feature.getAction());
            }
        }
    }

    /**
     * 完成无需发布动作的影子运行
     *
     * @param run       决策运行
     * @param dataLevel 数据质量等级
     * @param message   决策说明
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeUnpublished(DecisionRun run, String dataLevel, String message) {
        LocalDateTime now = LocalDateTime.now();
        run.setDataLevel(dataLevel);
        run.setStatus("SUCCESS");
        run.setMessage(trim(message));
        run.setFinishedAt(now);
        run.setPublished(0);
        run.setUpdateTime(now);
        if (decisionRunMapper.updateById(run) != 1) {
            throw new BusinessException("影子决策运行完成状态更新失败: " + run.getId());
        }
    }

    /**
     * 记录决策运行失败状态
     *
     * @param run   决策运行
     * @param error 原始异常
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(DecisionRun run, Throwable error) {
        if (Objects.isNull(run) || Objects.isNull(run.getId())) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        run.setStatus("FAILED");
        run.setMessage(trim(Objects.isNull(error) ? "决策失败" : error.getMessage()));
        run.setFinishedAt(now);
        run.setPublished(0);
        run.setUpdateTime(now);
        decisionRunMapper.updateById(run);
    }

    private DecisionFeatureSnapshot toSnapshot(DecisionRun run, DecisionFeature feature, LocalDateTime now) {
        return DecisionFeatureSnapshot.builder()
                .runId(run.getId())
                .code(feature.getCode())
                .action(feature.getAction())
                .featureVersion(run.getFeatureVersion())
                .featureHash(feature.getFeatureHash())
                .signalScore(feature.getSignalScore())
                .confluenceCount(feature.getConfluenceCount())
                .hotSourceCount(feature.getHotSourceCount())
                .mainlineMatch(Objects.isNull(feature.getMainlineMatch())
                        ? null : (Boolean.TRUE.equals(feature.getMainlineMatch()) ? 1 : 0))
                .valuationLevel(feature.getValuationLevel())
                .marketStance(feature.getMarketStance())
                .dataQuality(feature.getDataQuality())
                .selectionStatus(feature.getSelectionStatus())
                .rejectReason(feature.getRejectReason())
                .rankNo(feature.getRankNo())
                .featureJson(toJson(feature))
                .createTime(now)
                .updateTime(now)
                .deleted(0)
                .build();
    }

    private String dataCutoffJson(DecisionContext context) {
        return "{\"asOfTime\":\"" + context.getAsOfTime()
                + "\",\"policy\":\"" + context.getDataPolicy().name() + "\"}";
    }

    private String toJson(Object value) {
        return JsonUtils.toJsonString(value);
    }

    private String createRunNo(LocalDateTime now) {
        return now.format(RUN_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String trim(String message) {
        if (Objects.isNull(message) || message.length() <= 512) {
            return message;
        }
        return message.substring(0, 512);
    }
}
