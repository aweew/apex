package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.DecisionOutcome;
import com.awe.apex.quant.domain.dto.DecisionStrategyPerformance;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能决策结果归因 Mapper
 */
@Mapper
public interface DecisionOutcomeMapper extends BaseMapper<DecisionOutcome> {

    /**
     * 查询尚未完整归因的候选快照
     *
     * @return 待补算归因基础信息
     */
    @Select("""
            SELECT t1.id AS feature_snapshot_id,
                   t2.id AS action_id,
                   t1.run_id,
                   t1.code,
                   t3.action_date
            FROM decision_feature_snapshot t1
            INNER JOIN decision_run t3 ON t3.id = t1.run_id AND t3.deleted = 0
            LEFT JOIN daily_action t2 ON t2.run_id = t1.run_id
                                     AND t2.code = t1.code
                                     AND t2.action = t1.action
                                     AND t2.deleted = 0
            LEFT JOIN decision_outcome t4 ON t4.feature_snapshot_id = t1.id AND t4.deleted = 0
            WHERE t1.deleted = 0
              AND t1.action = 'BUY'
              AND t1.selection_status = 'SELECTED'
              AND t1.id IS NOT NULL
              AND t1.code IS NOT NULL
              AND t3.action_date IS NOT NULL
              AND t3.mode = 'LIVE'
              AND t3.status = 'SUCCESS'
              AND t3.published = 1
              AND (t4.id IS NULL OR t4.status <> 'COMPLETE')
            ORDER BY t3.action_date ASC, t1.id ASC
            """)
    List<DecisionOutcome> selectPendingOutcomes();

    /**
     * 按特征快照ID幂等保存归因结果
     *
     * @param outcome 归因结果
     * @return 影响行数
     */
    @Insert("""
            INSERT INTO decision_outcome (
                feature_snapshot_id, action_id, run_id, code, action_date, entry_date, entry_price, status,
                return_1d, return_3d, return_5d, return_10d, return_20d,
                excess_1d, excess_3d, excess_5d, excess_10d, excess_20d,
                mfe, mae, stop_hit, stop_hit_date, target_hit, target_hit_date,
                net_return, quality_status, calculated_at, create_time, update_time, deleted
            ) VALUES (
                #{featureSnapshotId}, #{actionId}, #{runId}, #{code}, #{actionDate},
                #{entryDate}, #{entryPrice}, #{status},
                #{return1d}, #{return3d}, #{return5d}, #{return10d}, #{return20d},
                #{excess1d}, #{excess3d}, #{excess5d}, #{excess10d}, #{excess20d},
                #{mfe}, #{mae}, #{stopHit}, #{stopHitDate}, #{targetHit}, #{targetHitDate},
                #{netReturn}, #{qualityStatus}, #{calculatedAt}, #{createTime}, #{updateTime}, 0
            )
            ON DUPLICATE KEY UPDATE
                feature_snapshot_id = VALUES(feature_snapshot_id),
                action_id = VALUES(action_id),
                run_id = VALUES(run_id),
                code = VALUES(code),
                action_date = VALUES(action_date),
                entry_date = VALUES(entry_date),
                entry_price = VALUES(entry_price),
                status = VALUES(status),
                return_1d = VALUES(return_1d),
                return_3d = VALUES(return_3d),
                return_5d = VALUES(return_5d),
                return_10d = VALUES(return_10d),
                return_20d = VALUES(return_20d),
                excess_1d = VALUES(excess_1d),
                excess_3d = VALUES(excess_3d),
                excess_5d = VALUES(excess_5d),
                excess_10d = VALUES(excess_10d),
                excess_20d = VALUES(excess_20d),
                mfe = VALUES(mfe),
                mae = VALUES(mae),
                stop_hit = VALUES(stop_hit),
                stop_hit_date = VALUES(stop_hit_date),
                target_hit = VALUES(target_hit),
                target_hit_date = VALUES(target_hit_date),
                net_return = VALUES(net_return),
                quality_status = VALUES(quality_status),
                calculated_at = VALUES(calculated_at),
                update_time = VALUES(update_time),
                deleted = 0
            """)
    int upsert(DecisionOutcome outcome);

    /**
     * 查询各策略已成熟的五日超额表现
     *
     * @param userId 所属用户ID
     * @return 策略表现聚合行
     */
    @Select("""
            SELECT JSON_UNQUOTE(JSON_EXTRACT(t1.feature_json, '$.strategyId')) AS strategyId,
                   COUNT(*) AS sampleCount,
                   AVG(t2.excess_5d) AS avgExcess5d,
                   AVG(CASE WHEN t2.excess_5d > 0 THEN 1 ELSE 0 END) AS winRate5d
            FROM decision_feature_snapshot t1
            INNER JOIN decision_outcome t2 ON t2.feature_snapshot_id = t1.id
                                           AND t2.deleted = 0
                                           AND t2.status = 'COMPLETE'
                                           AND t2.excess_5d IS NOT NULL
            INNER JOIN decision_run t3 ON t3.id = t1.run_id
                                      AND t3.deleted = 0
            WHERE t1.deleted = 0
              AND t1.action = 'BUY'
              AND t1.selection_status = 'SELECTED'
              AND t3.mode = 'LIVE'
              AND t3.status = 'SUCCESS'
              AND t3.published = 1
              AND t3.user_id = #{userId}
              AND t1.create_time >= DATE_SUB(NOW(), INTERVAL 365 DAY)
            GROUP BY JSON_UNQUOTE(JSON_EXTRACT(t1.feature_json, '$.strategyId'))
            """)
    List<DecisionStrategyPerformance> selectStrategyPerformance(@org.apache.ibatis.annotations.Param("userId") Long userId);
}
