package com.awe.apex.quant.signal.query;

import com.awe.apex.quant.domain.dto.ExternalMarketItemResp;
import com.awe.apex.quant.domain.dto.MarketIndexItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 策略信号页短线市场上下文。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortTermSignalResp {

    /** 市场数据截至日。 */
    private LocalDate dataAsOf;

    /** A股本地情绪温度。 */
    private ShortTermEmotionResp ashareEmotion;

    /** 主要指数。 */
    @Builder.Default
    private List<MarketIndexItem> indexes = new ArrayList<>();

    /** 上证关键阻力位。 */
    private BigDecimal shanghaiKeyResistance;

    /** 上证当前点位。 */
    private BigDecimal shanghaiCurrentPrice;

    /** 距离阻力位百分比。 */
    private BigDecimal resistanceDistancePct;

    /** 阻力位观察动作。 */
    private String resistanceAdvice;

    /** 三市成交额。 */
    private BigDecimal indexVolume;

    /** 三市成交额展示文案。 */
    private String indexVolumeText;

    /** 放量或缩量。 */
    private String volumeTrend;

    /** 较上一交易日成交额变化百分比。 */
    private BigDecimal volumeChangePct;

    /** 上涨家数。 */
    private Integer breadthUp;

    /** 下跌家数。 */
    private Integer breadthDown;

    /** 平盘家数。 */
    private Integer breadthFlat;

    /** 宏观和外围指标。 */
    @Builder.Default
    private List<ExternalMarketItemResp> macroItems = new ArrayList<>();

    /** VIX反向代理情绪。 */
    private ShortTermVixResp externalEmotion;

    /** 短线策略剧本。 */
    private ShortTermStrategyResp strategy;

    /** 已确认和观察中的候选。 */
    @Builder.Default
    private List<ShortTermCandidateResp> candidates = new ArrayList<>();

    /** 数据状态。 */
    private String dataStatus;

    /** 缺失数据项。 */
    @Builder.Default
    private List<String> missingData = new ArrayList<>();
}
