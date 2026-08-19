package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.DecisionMarketSignal;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

/**
 * 共享市场信号Mapper。
 */
@Mapper
public interface DecisionMarketSignalMapper extends BaseMapper<DecisionMarketSignal> {

    /**
     * 物理删除指定扫描的旧信号，支持同一快照重新生成。
     *
     * @param scanId 共享扫描ID
     * @return 删除数量
     */
    @Delete("DELETE FROM decision_market_signal WHERE scan_id = #{scanId}")
    int deleteByScanId(Long scanId);
}
