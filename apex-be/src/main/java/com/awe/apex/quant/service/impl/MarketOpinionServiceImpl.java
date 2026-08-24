package com.awe.apex.quant.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.awe.apex.common.exception.BusinessException;
import com.awe.apex.common.util.StringUtils;
import com.awe.apex.quant.config.ScriptDatabaseEnvironment;
import com.awe.apex.quant.domain.dto.MarketOpinionItemResp;
import com.awe.apex.quant.domain.dto.MarketOpinionRadarResp;
import com.awe.apex.quant.domain.entity.MarketOpinion;
import com.awe.apex.quant.mapper.MarketOpinionMapper;
import com.awe.apex.quant.service.IMarketOpinionService;
import com.awe.apex.quant.util.PythonCommandResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 可追溯市场观点服务实现。
 */
@Slf4j
@Service
public class MarketOpinionServiceImpl implements IMarketOpinionService {

    private static final int OPINION_LIMIT = 80;

    @Resource
    private MarketOpinionMapper marketOpinionMapper;

    @Resource
    private ScriptDatabaseEnvironment scriptDatabaseEnvironment;

    @Value("${apex.market-opinion.python-cmd:${apex.news.python-cmd:python}}")
    private String pythonCmd;

    @Value("${apex.market-opinion.script-path:}")
    private String scriptPathConfig;

    /**
     * 读取近五日已收录的可追溯观点。
     *
     * @return 市场观点雷达
     */
    @Override
    public MarketOpinionRadarResp radar() {
        List<MarketOpinion> opinionRows = marketOpinionMapper.selectList(Wrappers.<MarketOpinion>lambdaQuery()
                .ge(MarketOpinion::getPublishedAt, LocalDateTime.now().minusDays(5))
                .orderByDesc(MarketOpinion::getPublishedAt)
                .orderByDesc(MarketOpinion::getId)
                .last("LIMIT 120"));
        if (Objects.isNull(opinionRows)) {
            opinionRows = List.of();
        }
        List<MarketOpinionItemResp> institutionViews = new ArrayList<>();
        List<MarketOpinionItemResp> activeSeats = new ArrayList<>();
        List<MarketOpinionItemResp> kolViews = new ArrayList<>();
        LocalDateTime snapshotTime = null;
        for (MarketOpinion opinionRow : opinionRows) {
            if (Objects.nonNull(opinionRow.getSnapshotTime())
                    && (Objects.isNull(snapshotTime) || opinionRow.getSnapshotTime().isAfter(snapshotTime))) {
                snapshotTime = opinionRow.getSnapshotTime();
            }
            if ("INSTITUTION".equals(opinionRow.getOpinionType()) && institutionViews.size() < 4) {
                institutionViews.add(toItem(opinionRow));
            } else if ("ACTIVE_SEAT".equals(opinionRow.getOpinionType()) && activeSeats.size() < 3) {
                activeSeats.add(toItem(opinionRow));
            } else if ("KOL".equals(opinionRow.getOpinionType()) && kolViews.size() < 3) {
                kolViews.add(toItem(opinionRow));
            }
        }
        return MarketOpinionRadarResp.builder()
                .institutionViews(institutionViews)
                .activeSeats(activeSeats)
                .kolViews(kolViews)
                .consensus(buildConsensus(opinionRows))
                .divergence(buildDivergence(opinionRows))
                .kolSourceStatus(CollUtil.isEmpty(kolViews) ? "大V公开授权源未接入" : "仅展示已授权公开原帖")
                .snapshotTime(snapshotTime)
                .build();
    }

    /**
     * 运行公开观点同步脚本。
     */
    @Override
    public void refresh() {
        Path script = resolveScript();
        if (Objects.isNull(script) || !Files.isRegularFile(script)) {
            throw new BusinessException("未找到市场观点同步脚本 sync_market_opinions.py");
        }
        List<String> command = List.of(
                PythonCommandResolver.resolve(pythonCmd), "-u", script.toAbsolutePath().toString(),
                "--limit", String.valueOf(OPINION_LIMIT));
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(script.getParent().toFile());
            processBuilder.redirectErrorStream(true);
            scriptDatabaseEnvironment.apply(processBuilder.environment());
            Process process = processBuilder.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                    if (output.length() > 8000) {
                        break;
                    }
                }
            }
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BusinessException("市场观点同步超时（>120s）");
            }
            if (process.exitValue() != 0) {
                throw new BusinessException("市场观点同步失败：" + trimOutput(output.toString()));
            }
            log.info("市场观点同步完成，输出={}", trimOutput(output.toString()));
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("市场观点同步异常，原因={}", ex.getMessage());
            throw new BusinessException("市场观点同步异常：" + ex.getMessage());
        }
    }

    private MarketOpinionItemResp toItem(MarketOpinion opinionRow) {
        return MarketOpinionItemResp.builder()
                .opinionType(opinionRow.getOpinionType())
                .subjectName(opinionRow.getSubjectName())
                .source(opinionRow.getSource())
                .title(opinionRow.getTitle())
                .summary(opinionRow.getSummary())
                .direction(opinionRow.getDirection())
                .relatedCode(opinionRow.getRelatedCode())
                .relatedName(opinionRow.getRelatedName())
                .topic(opinionRow.getTopic())
                .netAmount(opinionRow.getNetAmount())
                .publishedAt(opinionRow.getPublishedAt())
                .url(opinionRow.getUrl())
                .build();
    }

    private String buildConsensus(List<MarketOpinion> opinionRows) {
        int positiveCount = 0;
        int negativeCount = 0;
        int neutralCount = 0;
        Set<String> institutionNames = new HashSet<>();
        for (MarketOpinion opinionRow : opinionRows) {
            if (!"INSTITUTION".equals(opinionRow.getOpinionType())) {
                continue;
            }
            if (StringUtils.isNotBlank(opinionRow.getSubjectName())) {
                institutionNames.add(opinionRow.getSubjectName());
            }
            String direction = normalizeDirection(opinionRow.getDirection());
            if ("POSITIVE".equals(direction)) {
                positiveCount++;
            } else if ("NEGATIVE".equals(direction)) {
                negativeCount++;
            } else {
                neutralCount++;
            }
        }
        int total = positiveCount + negativeCount + neutralCount;
        if (total == 0) {
            return "近5日暂未收录机构公开观点";
        }
        if (institutionNames.size() < 2) {
            return "近5日仅收录 " + institutionNames.size() + " 家机构观点，暂不形成跨机构共识";
        }
        String stance = positiveCount > negativeCount ? "偏积极" : positiveCount < negativeCount ? "偏审慎" : "无明显方向";
        return "近5日收录 " + total + " 条机构观点，来自 " + institutionNames.size() + " 家机构，整体" + stance
                + "（积极 " + positiveCount + " / 审慎 " + negativeCount + " / 中性 " + neutralCount + "）";
    }

    private String buildDivergence(List<MarketOpinion> opinionRows) {
        Map<String, Map<String, Set<String>>> topicDirections = new HashMap<>();
        for (MarketOpinion opinionRow : opinionRows) {
            if (!"INSTITUTION".equals(opinionRow.getOpinionType()) || StringUtils.isBlank(opinionRow.getTopic())) {
                continue;
            }
            String direction = normalizeDirection(opinionRow.getDirection());
            if (!"NEUTRAL".equals(direction)) {
                topicDirections.computeIfAbsent(opinionRow.getTopic(), key -> new HashMap<>())
                        .computeIfAbsent(direction, key -> new HashSet<>())
                        .add(opinionRow.getSubjectName());
            }
        }
        for (Map.Entry<String, Map<String, Set<String>>> entry : topicDirections.entrySet()) {
            Set<String> positiveInstitutions = entry.getValue().get("POSITIVE");
            Set<String> negativeInstitutions = entry.getValue().get("NEGATIVE");
            if (CollUtil.isNotEmpty(positiveInstitutions) && CollUtil.isNotEmpty(negativeInstitutions)
                    && !positiveInstitutions.equals(negativeInstitutions)) {
                return entry.getKey() + " 同时出现积极与审慎评级，请对照原研报判断";
            }
        }
        return "已收录观点暂未出现同主题相反评级";
    }

    private String normalizeDirection(String direction) {
        if (StringUtils.contains(direction, "买入") || StringUtils.contains(direction, "增持")
                || StringUtils.contains(direction, "推荐") || StringUtils.contains(direction, "看多")) {
            return "POSITIVE";
        }
        if (StringUtils.contains(direction, "卖出") || StringUtils.contains(direction, "减持")
                || StringUtils.contains(direction, "回避") || StringUtils.contains(direction, "看空")) {
            return "NEGATIVE";
        }
        return "NEUTRAL";
    }

    private Path resolveScript() {
        if (StringUtils.isNotBlank(scriptPathConfig)) {
            return Paths.get(scriptPathConfig.trim());
        }
        Path currentDir = Paths.get(System.getProperty("user.dir"));
        List<Path> candidates = List.of(
                currentDir.resolve("../scripts/market_data/sync_market_opinions.py"),
                currentDir.resolve("scripts/market_data/sync_market_opinions.py"),
                currentDir.resolve("../../scripts/market_data/sync_market_opinions.py"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate.normalize();
            }
        }
        return null;
    }

    private String trimOutput(String output) {
        if (StringUtils.isBlank(output)) {
            return "无输出";
        }
        String normalized = output.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
