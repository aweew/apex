package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.dto.ApexAiPortfolioOption;
import com.awe.apex.quant.domain.dto.ApexAiStrategyOption;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * Apex AI 轻量上下文查询。
 */
@Mapper
public interface ApexAiQueryMapper {

    /**
     * 查询当前用户可分析的组合选项。
     *
     * @param userId  当前用户ID
     * @param isAdmin 是否管理员
     * @return 组合选项
     */
    @Select("""
            SELECT t1.id,
                   CASE WHEN t1.user_id = #{userId} AND t1.is_default = 1
                        THEN '我的持仓' ELSE t1.name END AS name,
                   CASE WHEN t1.user_id = #{userId} AND t1.is_default = 1
                        THEN TRUE ELSE FALSE END AS defaultPortfolio,
                   COUNT(t2.id) AS positionCount
            FROM portfolio t1
            LEFT JOIN portfolio_holding t2 ON t2.portfolio_id = t1.id
                                              AND t2.deleted = 0
            WHERE t1.deleted = 0
              AND t1.status = 'ACTIVE'
              AND (t1.user_id = #{userId} OR #{isAdmin} = TRUE)
            GROUP BY t1.id, t1.user_id, t1.name, t1.is_default, t1.sort_no, t1.update_time
            ORDER BY CASE WHEN t1.user_id = #{userId} AND t1.is_default = 1 THEN 0 ELSE 1 END,
                     t1.sort_no ASC,
                     t1.update_time DESC
            """)
    List<ApexAiPortfolioOption> selectPortfolioOptions(@Param("userId") Long userId,
                                                        @Param("isAdmin") boolean isAdmin);

    /**
     * 聚合最近决策日的策略归因选项。
     *
     * @param userId 当前用户ID
     * @param days   最近决策日数量
     * @return 策略选项
     */
    @Select("""
            SELECT t1.strategy_id AS strategyId,
                   t1.strategy_id AS strategyName,
                   COUNT(t2.return_1d) AS measuredCount,
                   AVG(t2.return_1d) * 100 AS avgNextPct,
                   AVG(CASE WHEN t2.return_1d > 0 THEN 1 ELSE 0 END) * 100 AS winRate
            FROM daily_action t1
            INNER JOIN (
                SELECT t3.action_date
                FROM daily_action t3
                WHERE t3.user_id = #{userId}
                  AND t3.deleted = 0
                  AND t3.action_date IS NOT NULL
                GROUP BY t3.action_date
                ORDER BY t3.action_date DESC
                LIMIT #{days}
            ) t4 ON t4.action_date = t1.action_date
            LEFT JOIN decision_outcome t2 ON t2.action_id = t1.id
                                            AND t2.deleted = 0
                                            AND t2.return_1d IS NOT NULL
            WHERE t1.user_id = #{userId}
              AND t1.deleted = 0
              AND t1.action = 'BUY'
              AND t1.strategy_id IS NOT NULL
              AND t1.strategy_id <> ''
            GROUP BY t1.strategy_id
            ORDER BY avgNextPct IS NULL, avgNextPct ASC, strategyId ASC
            """)
    List<ApexAiStrategyOption> selectStrategyOptions(@Param("userId") Long userId,
                                                      @Param("days") int days);
}
