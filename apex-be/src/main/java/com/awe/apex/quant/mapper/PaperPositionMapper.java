package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.PaperPosition;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PaperPositionMapper extends BaseMapper<PaperPosition> {

    /**
     * 恢复软删除持仓并重置数量成本（绕过逻辑删除过滤）
     */
    @Update("UPDATE paper_position SET deleted=0, quantity=#{quantity}, cost_price=#{costPrice}, "
            + "stop_loss=#{stopLoss}, take_profit=#{takeProfit}, name=#{name}, update_time=#{updateTime} "
            + "WHERE account_id=#{accountId} AND code=#{code}")
    int restoreAndSet(@Param("accountId") Long accountId,
                      @Param("code") String code,
                      @Param("quantity") Integer quantity,
                      @Param("costPrice") BigDecimal costPrice,
                      @Param("stopLoss") BigDecimal stopLoss,
                      @Param("takeProfit") BigDecimal takeProfit,
                      @Param("name") String name,
                      @Param("updateTime") LocalDateTime updateTime);
}
