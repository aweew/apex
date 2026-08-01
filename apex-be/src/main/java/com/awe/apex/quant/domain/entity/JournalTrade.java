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
@TableName("journal_trade")
public class JournalTrade implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 成交日 */ private LocalDate tradeDate;
    /** 证券代码 */ private String code;
    /** BUY/SELL */ private String side;
    /** 成交价 */ private BigDecimal price;
    /** 数量 */ private Integer quantity;
    /** 成交额 */ private BigDecimal amount;
    /** 关联清单ID */ private Long relatedActionId;
    /** 备注 */ private String note;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic private Integer deleted;
}
