package com.awe.apex.quant.mapper;

import com.awe.apex.quant.domain.entity.ApexUserProfile;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Apex 用户档案数据访问层
 */
@Mapper
public interface ApexUserProfileMapper extends BaseMapper<ApexUserProfile> {
}
