package com.awe.apex.quant.signal.query;

import com.awe.apex.quant.domain.entity.BarDaily;
import com.awe.apex.quant.signal.event.SignalCalculationRunWriteBO;
import com.awe.apex.quant.signal.event.SignalDefinitionRuleBO;
import com.awe.apex.quant.signal.event.SignalEventWriteBO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 市场行为信号中心数据访问。
 */
@Mapper
public interface SignalCenterMapper {

    /**
     * 查询最近完整日线日期。
     *
     * @return 最近交易日
     */
    @Select("SELECT MAX(t1.trade_date) FROM bar_daily t1 WHERE t1.deleted = 0")
    LocalDate selectLatestTradeDate();

    /**
     * 查询全市场有效证券代码。
     *
     * @return 证券代码
     */
    @Select("""
            SELECT t1.code
            FROM stock_basic t1
            WHERE t1.deleted = 0
              AND (t1.st_flag IS NULL OR t1.st_flag = 0)
            ORDER BY t1.code ASC
            """)
    List<String> selectUniverseSymbols();

    /**
     * 查询检测使用的完整量价日线。
     *
     * @param symbol 证券代码
     * @param asOfDate 截止日期
     * @param limit 最大根数
     * @return 升序日线
     */
    @Select("""
            SELECT t1.code,
                   t1.trade_date,
                   t1.open_price,
                   t1.high_price,
                   t1.low_price,
                   t1.close_price,
                   t1.volume,
                   t1.amount,
                   t1.pct_chg,
                   t1.turnover_rate
            FROM (
                SELECT t2.code,
                       t2.trade_date,
                       t2.open_price,
                       t2.high_price,
                       t2.low_price,
                       t2.close_price,
                       t2.volume,
                       t2.amount,
                       t2.pct_chg,
                       t2.turnover_rate
                FROM bar_daily t2
                WHERE t2.code = #{symbol}
                  AND t2.trade_date <= #{asOfDate}
                  AND t2.deleted = 0
                ORDER BY t2.trade_date DESC
                LIMIT #{limit}
            ) t1
            ORDER BY t1.trade_date ASC
            """)
    List<BarDaily> selectBehaviorBars(@Param("symbol") String symbol,
                                      @Param("asOfDate") LocalDate asOfDate,
                                      @Param("limit") int limit);

    /**
     * 查询当前生效规则标识。
     *
     * @return 定义和规则
     */
    @Select("""
            SELECT t1.id AS definition_id,
                   t2.id AS rule_id,
                   t1.signal_code,
                   t2.rule_version
            FROM signal_definition t1
            INNER JOIN signal_rule t2 ON t2.definition_id = t1.id
                                      AND t2.rule_status = 'ACTIVE'
                                      AND t2.deleted = 0
            WHERE t1.enabled = 1
              AND t1.deleted = 0
            ORDER BY t1.signal_code ASC
            """)
    List<SignalDefinitionRuleBO> selectActiveDefinitionRules();

    /**
     * 创建计算批次。
     *
     * @param run 批次
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO signal_calculation_run
                (run_no, trigger_type, timeframe, as_of_time, feature_version,
                 rule_set_checksum, run_status, total_count, started_at)
            VALUES
                (#{runNo}, #{triggerType}, #{timeframe}, #{asOfTime}, 'daily-v1',
                 SHA2('signal-center-mvp-1', 256), 'RUNNING', #{totalCount}, CURRENT_TIMESTAMP)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCalculationRun(SignalCalculationRunWriteBO run);

    /**
     * 完成计算批次。
     *
     * @param id 批次ID
     * @param status 状态
     * @param successCount 成功数
     * @param failureCount 失败数
     * @param errorMessage 错误摘要
     * @return 影响行数
     */
    @Update("""
            UPDATE signal_calculation_run
            SET run_status = #{status},
                success_count = #{successCount},
                failure_count = #{failureCount},
                error_message = #{errorMessage},
                finished_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int finishCalculationRun(@Param("id") Long id,
                             @Param("status") String status,
                             @Param("successCount") int successCount,
                             @Param("failureCount") int failureCount,
                             @Param("errorMessage") String errorMessage);

    /**
     * 查询计算批次。
     *
     * @param runNo 批次号
     * @return 批次状态
     */
    @Select("""
            SELECT t1.run_no,
                   t1.run_status AS status,
                   t1.total_count,
                   t1.success_count,
                   t1.failure_count,
                   t1.as_of_time,
                   t1.error_message
            FROM signal_calculation_run t1
            WHERE t1.run_no = #{runNo}
            """)
    SignalCalculationRunResp selectCalculationRun(@Param("runNo") String runNo);

    /**
     * 幂等写入市场行为事件。
     *
     * @param event 事件
     * @return 影响行数
     */
    @Insert("""
            INSERT IGNORE INTO signal_event
                (event_no, definition_id, rule_id, calculation_run_id, instrument_type,
                 symbol, timeframe, trigger_time, as_of_time, signal_direction,
                 lifecycle_state, strength_score, confidence_score, risk_score,
                 valid_until, evidence_json, data_status, feature_version, published)
            VALUES
                (#{eventNo}, #{definitionId}, #{ruleId}, #{calculationRunId}, 'STOCK',
                 #{symbol}, #{timeframe}, #{triggerTime}, #{asOfTime}, #{direction},
                 #{lifecycleState}, #{strength}, #{confidence}, #{riskScore},
                 #{validUntil}, CAST(#{evidenceJson} AS JSON), #{dataStatus}, #{featureVersion}, 1)
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertEvent(SignalEventWriteBO event);

    /**
     * 查询幂等事件ID。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @param definitionId 定义ID
     * @param ruleId 规则ID
     * @param triggerTime 触发时间
     * @return 事件ID
     */
    @Select("""
            SELECT t1.id
            FROM signal_event t1
            WHERE t1.instrument_type = 'STOCK'
              AND t1.symbol = #{symbol}
              AND t1.timeframe = #{timeframe}
              AND t1.definition_id = #{definitionId}
              AND t1.rule_id = #{ruleId}
              AND t1.trigger_time = #{triggerTime}
            """)
    Long selectEventId(@Param("symbol") String symbol,
                       @Param("timeframe") String timeframe,
                       @Param("definitionId") Long definitionId,
                       @Param("ruleId") Long ruleId,
                       @Param("triggerTime") LocalDateTime triggerTime);

    /**
     * 更新当前查询快照。
     *
     * @param event 事件
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO signal_snapshot
                (instrument_type, symbol, timeframe, definition_id, event_id, lifecycle_state,
                 strength_score, confidence_score, risk_score, snapshot_time,
                 freshness_status, evidence_summary_json)
            VALUES
                ('STOCK', #{symbol}, #{timeframe}, #{definitionId}, #{id}, #{lifecycleState},
                 #{strength}, #{confidence}, #{riskScore}, #{asOfTime},
                 CASE WHEN #{dataStatus} = 'COMPLETE' THEN 'FRESH' ELSE 'PARTIAL' END,
                 CAST(#{evidenceJson} AS JSON))
            ON DUPLICATE KEY UPDATE
                event_id = VALUES(event_id),
                lifecycle_state = VALUES(lifecycle_state),
                strength_score = VALUES(strength_score),
                confidence_score = VALUES(confidence_score),
                risk_score = VALUES(risk_score),
                snapshot_time = VALUES(snapshot_time),
                freshness_status = VALUES(freshness_status),
                evidence_summary_json = VALUES(evidence_summary_json)
            """)
    int upsertSnapshot(SignalEventWriteBO event);

    /**
     * 写入事件首条生命周期记录。
     *
     * @param event 事件
     * @return 影响行数
     */
    @Insert("""
            INSERT IGNORE INTO signal_lifecycle
                (event_id, sequence_no, from_state, to_state, transition_time,
                 as_of_time, reason_code, evidence_json, calculation_run_id)
            VALUES
                (#{id}, 1, NULL, #{lifecycleState}, #{triggerTime},
                 #{asOfTime}, 'RULE_TRIGGERED', CAST(#{evidenceJson} AS JSON), #{calculationRunId})
            """)
    int insertInitialLifecycle(SignalEventWriteBO event);

    /**
     * 查询信号中心概览。
     *
     * @param timeframe 周期
     * @return 概览
     */
    @Select("""
            SELECT COALESCE(SUM(CASE WHEN t2.signal_direction = 'BULLISH' THEN 1 ELSE 0 END), 0) AS bullish_count,
                   COALESCE(SUM(CASE WHEN t2.signal_direction = 'BEARISH' THEN 1 ELSE 0 END), 0) AS bearish_count,
                   COALESCE(SUM(CASE WHEN t2.signal_direction = 'RISK' THEN 1 ELSE 0 END), 0) AS risk_count,
                   COALESCE(SUM(CASE WHEN t1.lifecycle_state IN ('CONFIRMED','STRENGTHENING','ACTIVE') THEN 1 ELSE 0 END), 0) AS confirmed_count,
                   MAX(t1.snapshot_time) AS data_as_of,
                   CASE WHEN SUM(CASE WHEN t1.freshness_status = 'PARTIAL' THEN 1 ELSE 0 END) > 0 THEN 'PARTIAL' ELSE 'COMPLETE' END AS data_status,
                   MAX(t3.feature_version) AS feature_version
            FROM signal_snapshot t1
            INNER JOIN signal_definition t2 ON t2.id = t1.definition_id
            INNER JOIN signal_event t3 ON t3.id = t1.event_id
            WHERE t1.timeframe = #{timeframe}
            """)
    SignalOverviewResp selectOverview(@Param("timeframe") String timeframe);

    /**
     * 查询市场行为排行榜。
     *
     * @param timeframe 周期
     * @param direction 方向
     * @param lifecycleState 生命周期
     * @param minStrength 最低强度
     * @param size 数量
     * @return 排行项
     */
    @Select("""
            <script>
            SELECT t1.event_id,
                   t1.symbol,
                   t4.name,
                   t2.signal_code,
                   t2.signal_name,
                   t2.signal_direction AS direction,
                   t1.lifecycle_state,
                   t1.strength_score AS strength,
                   t1.confidence_score AS confidence,
                   t1.probability_value AS probability,
                   t1.risk_score,
                   t1.timeframe,
                   t3.trigger_time,
                   t3.as_of_time AS data_as_of,
                   t3.data_status,
                   t3.evidence_json
            FROM signal_snapshot t1
            INNER JOIN signal_definition t2 ON t2.id = t1.definition_id
            INNER JOIN signal_event t3 ON t3.id = t1.event_id
            LEFT JOIN stock_basic t4 ON t4.code = t1.symbol AND t4.deleted = 0
            WHERE t1.timeframe = #{timeframe}
            <if test="direction != null and direction != ''">
              AND t2.signal_direction = #{direction}
            </if>
            <if test="lifecycleState != null and lifecycleState != ''">
              AND t1.lifecycle_state = #{lifecycleState}
            </if>
            <if test="minStrength != null">
              AND t1.strength_score &gt;= #{minStrength}
            </if>
            ORDER BY CASE WHEN t2.signal_direction = 'RISK' THEN t1.risk_score ELSE t1.strength_score END DESC,
                     t1.confidence_score DESC,
                     t1.snapshot_time DESC
            LIMIT #{size}
            </script>
            """)
    List<SignalRankingItemResp> selectRankings(@Param("timeframe") String timeframe,
                                                @Param("direction") String direction,
                                                @Param("lifecycleState") String lifecycleState,
                                                @Param("minStrength") BigDecimal minStrength,
                                                @Param("size") int size);

    /**
     * 查询个股当前市场行为。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @return 当前行为
     */
    @Select("""
            SELECT t1.event_id,
                   t1.symbol,
                   t4.name,
                   t2.signal_code,
                   t2.signal_name,
                   t2.signal_direction AS direction,
                   t1.lifecycle_state,
                   t1.strength_score AS strength,
                   t1.confidence_score AS confidence,
                   t1.probability_value AS probability,
                   t1.risk_score,
                   t1.timeframe,
                   t3.trigger_time,
                   t3.as_of_time AS data_as_of,
                   t3.data_status,
                   t3.evidence_json
            FROM signal_snapshot t1
            INNER JOIN signal_definition t2 ON t2.id = t1.definition_id
            INNER JOIN signal_event t3 ON t3.id = t1.event_id
            LEFT JOIN stock_basic t4 ON t4.code = t1.symbol AND t4.deleted = 0
            WHERE t1.symbol = #{symbol}
              AND t1.timeframe = #{timeframe}
            ORDER BY t1.snapshot_time DESC, t1.strength_score DESC
            """)
    List<SignalRankingItemResp> selectStockSignals(@Param("symbol") String symbol,
                                                    @Param("timeframe") String timeframe);

    /**
     * 查询个股生命周期时间轴。
     *
     * @param symbol 证券代码
     * @param timeframe 周期
     * @param size 数量
     * @return 时间轴
     */
    @Select("""
            SELECT t1.event_id,
                   t3.signal_code,
                   t3.signal_name,
                   t1.from_state,
                   t1.to_state,
                   t1.reason_code,
                   t1.transition_time AS event_time,
                   t1.as_of_time AS data_as_of,
                   t1.evidence_json
            FROM signal_lifecycle t1
            INNER JOIN signal_event t2 ON t2.id = t1.event_id
            INNER JOIN signal_definition t3 ON t3.id = t2.definition_id
            WHERE t2.symbol = #{symbol}
              AND t2.timeframe = #{timeframe}
              AND t2.deleted = 0
            ORDER BY t1.transition_time DESC, t1.sequence_no DESC
            LIMIT #{size}
            """)
    List<SignalTimelineItemResp> selectTimeline(@Param("symbol") String symbol,
                                                @Param("timeframe") String timeframe,
                                                @Param("size") int size);

    /**
     * 查询启用信号定义。
     *
     * @return 信号定义
     */
    @Select("""
            SELECT t1.signal_code,
                   t1.signal_name,
                   t1.signal_category AS category,
                   t1.signal_direction AS direction,
                   t1.description,
                   t2.rule_version,
                   t2.feature_version
            FROM signal_definition t1
            INNER JOIN signal_rule t2 ON t2.definition_id = t1.id
                                      AND t2.rule_status = 'ACTIVE'
                                      AND t2.deleted = 0
            WHERE t1.enabled = 1
              AND t1.deleted = 0
            ORDER BY t1.signal_code ASC
            """)
    List<SignalDefinitionResp> selectDefinitions();
}
