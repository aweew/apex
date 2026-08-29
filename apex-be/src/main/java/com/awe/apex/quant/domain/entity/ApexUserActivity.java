package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户功能使用事件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("apex_user_activity")
public class ApexUserActivity {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 事件类型 */
    private String activityType;

    /** 功能模块编码 */
    private String moduleCode;

    /** 功能模块名称 */
    private String moduleName;

    /** 发生时间 */
    private LocalDateTime occurredAt;

    /** 创建时间 */
    private LocalDateTime createTime;
}
