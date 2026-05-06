package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调用日志实体
 *
 * <p>记录每次 AI 调用的完整明细，用于审计和统计。
 * 表按月分区，自动归档。
 *
 * @since 1.0.0
 */
@Data
@TableName("call_logs")
public class CallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** API Key ID */
    private Long apiKeyId;

    /** API Key 名称 */
    private String apiKeyName;

    /** 用户ID */
    private Long userId;

    /** 请求模型（统一模型名） */
    private String model;

    /** 服务提供商 */
    private String provider;

    /** 实际模型名（渠道映射后） */
    private String actualModel;

    /** 请求唯一ID */
    private String requestId;

    /** 输入内容（截断） */
    private String inputContent;

    /** 输入Token数 */
    private Integer inputTokens;

    /** 输出内容（截断） */
    private String outputContent;

    /** 输出Token数 */
    private Integer outputTokens;

    /** 总Token数 */
    private Integer totalTokens;

    /** 总延迟（毫秒） */
    private Integer latencyMs;

    /** 首Token延迟（毫秒） */
    private Integer firstTokenLatencyMs;

    /** 渠道ID */
    private Long channelId;

    /** 渠道名称 */
    private String channelName;

    /** 结束原因 */
    private String finishReason;

    /** 错误码 */
    private String errorCode;

    /** 错误消息 */
    private String errorMessage;

    /** 客户端IP */
    private String clientIp;

    /** User-Agent */
    private String userAgent;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否成功调用
     */
    public boolean isSuccess() {
        return errorCode == null || errorCode.isEmpty();
    }

    /**
     * 获取耗时描述
     */
    public String getLatencyDesc() {
        if (latencyMs == null) return "N/A";
        if (latencyMs < 1000) return latencyMs + "ms";
        return String.format("%.2fs", latencyMs / 1000.0);
    }
}