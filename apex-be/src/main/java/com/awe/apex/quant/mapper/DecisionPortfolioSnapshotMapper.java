package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.DecisionPortfolioSnapshot;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * 决策组合快照 Mapper
 */
@Mapper
public interface DecisionPortfolioSnapshotMapper extends BaseMapper<DecisionPortfolioSnapshot> {

    /**
     * 查询指定决策日最近一次成功的非回放组合快照
     *
     * @param userId     所属用户ID
     * @param actionDate 决策日
     * @return 历史组合快照
     */
    @Select("""
            SELECT t1.*
            FROM decision_portfolio_snapshot t1
            INNER JOIN decision_run t2 ON t2.id = t1.run_id AND t2.deleted = 0
            WHERE t1.action_date = #{actionDate}
              AND t1.deleted = 0
              AND t2.mode = 'LIVE'
              AND t2.status = 'SUCCESS'
              AND t2.published = 1
              AND t2.user_id = #{userId}
            ORDER BY t1.id DESC
            LIMIT 1
            """)
    DecisionPortfolioSnapshot selectHistorical(@Param("userId") Long userId,
                                               @Param("actionDate") LocalDate actionDate);
}
