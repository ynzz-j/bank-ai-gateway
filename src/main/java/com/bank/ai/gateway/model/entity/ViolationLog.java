package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违规记录实体
 */
@Data
@TableName("violation_logs")
public class ViolationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** API Key ID */
    private Long apiKeyId;

    /** 用户ID */
    private Long userId;

    /** 调用日志ID */
    private Long callLogId;

    /** 触发的过滤器名称 */
    private String filterName;

    /** 命中的敏感词/规则 */
    private String matchedWord;

    /** 分类 */
    private String category;

    /** 处理动作：BLOCK/WARN/MASK */
    private String action;

    /** 命中内容片段（脱敏后） */
    private String contentSnippet;

    /** 方向：INPUT/OUTPUT */
    private String direction;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
