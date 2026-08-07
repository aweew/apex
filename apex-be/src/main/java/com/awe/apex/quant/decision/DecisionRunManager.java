package com.awe.apex.quant.decision;

import com.awe.apex.quant.domain.entity.DecisionFeatureSnapshot;
import com.awe.apex.quant.domain.entity.DecisionRun;
import com.awe.apex.quant.mapper.DecisionFeatureSnapshotMapper;
import com.awe.apex.quant.mapper.DecisionRunMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class DecisionRunManager {

    public static final String RULE_VERSION = "RULE_V1";
    public static final String MODEL_VERSION = "RULE_CHAMPION_V1";
    public static final String FEATURE_VERSION = "FEATURE_V1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter RUN_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private DecisionRunMapper decisionRunMapper;

    @Resource
    private DecisionFeatureSnapshotMapper featureSnapshotMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DecisionRun start(DecisionContext context, String groupName) {
        return start(context, groupName, Map.of());
    }

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
        decisionRunMapper.insert(run);
        return run;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveFeatures(DecisionRun run, List<DecisionFeature> features) {
        if (run == null || run.getId() == null || features == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (DecisionFeature feature : features) {
            featureSnapshotMapper.insert(toSnapshot(run, feature, now));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeUnpublished(DecisionRun run, String dataLevel, String message) {
        LocalDateTime now = LocalDateTime.now();
        run.setDataLevel(dataLevel);
        run.setStatus("SUCCESS");
        run.setMessage(trim(message));
        run.setFinishedAt(now);
        run.setPublished(0);
        run.setUpdateTime(now);
        decisionRunMapper.updateById(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(DecisionRun run, Throwable error) {
        if (run == null || run.getId() == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        run.setStatus("FAILED");
        run.setMessage(trim(error == null ? "决策失败" : error.getMessage()));
        run.setFinishedAt(now);
        run.setPublished(0);
        run.setUpdateTime(now);
        decisionRunMapper.updateById(run);
    }

    private DecisionFeatureSnapshot toSnapshot(DecisionRun run, DecisionFeature feature, LocalDateTime now) {
        return DecisionFeatureSnapshot.builder()
                .runId(run.getId())
                .code(feature.code())
                .action(feature.action())
                .featureVersion(run.getFeatureVersion())
                .featureHash(feature.featureHash())
                .signalScore(feature.signalScore())
                .confluenceCount(feature.confluenceCount())
                .hotSourceCount(feature.hotSourceCount())
                .mainlineMatch(feature.mainlineMatch() == null
                        ? null : (Boolean.TRUE.equals(feature.mainlineMatch()) ? 1 : 0))
                .valuationLevel(feature.valuationLevel())
                .marketStance(feature.marketStance())
                .dataQuality(feature.dataQuality())
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
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("决策特征序列化失败", ex);
        }
    }

    private String createRunNo(LocalDateTime now) {
        return now.format(RUN_TIME) + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String trim(String message) {
        if (message == null || message.length() <= 512) {
            return message;
        }
        return message.substring(0, 512);
    }
}
