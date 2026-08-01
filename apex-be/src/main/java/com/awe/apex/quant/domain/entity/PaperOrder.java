package com.awe.apex.quant.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("paper_order")
public class PaperOrder implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 账户ID */ private Long accountId;
    /** 证券代码 */ private String code;
    /** BUY/SELL */ private String side;
    /** 数量 */ private Integer quantity;
    /** 成交价 */ private BigDecimal price;
    /** 成交额 */ private BigDecimal amount;
    /** 费用 */ private BigDecimal fee;
    /** 成交日 */ private LocalDate tradeDate;
    /** 状态 */ private String status;
    /** 原因 */ private String reason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
