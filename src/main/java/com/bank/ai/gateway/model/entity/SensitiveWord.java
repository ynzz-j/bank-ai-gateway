package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感词实体
 */
@Data
@TableName("sensitive_words")
public class SensitiveWord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 敏感词内容 */
    private String word;

    /** 分类：POLITICS/PORNOGRAPHY/VIOLENCE/FRAUD/CUSTOM等 */
    private String category;

    /** 处理动作：BLOCK-拦截/WARN-警告/MASK-脱敏 */
    private String action;

    /** 是否启用：1-启用 0-禁用 */
    private Short enabled;

    /** 创建人 */
    private Long createdBy;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
