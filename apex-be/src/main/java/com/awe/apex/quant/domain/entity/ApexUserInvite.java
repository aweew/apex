package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Apex 私有邀请
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("apex_user_invite")
public class ApexUserInvite implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 邀请令牌 SHA-256 摘要 */
    private String tokenHash;

    /** 创建管理员ID */
    private Long creatorUserId;

    /** INVITE/RESET */
    private String purpose;

    /** 重置目标用户ID */
    private Long targetUserId;

    /** 使用用户ID */
    private Long usedUserId;

    /** 有效期 */
    private LocalDateTime expireTime;

    /** 使用时间 */
    private LocalDateTime usedTime;

    /** 作废时间 */
    private LocalDateTime revokedTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
