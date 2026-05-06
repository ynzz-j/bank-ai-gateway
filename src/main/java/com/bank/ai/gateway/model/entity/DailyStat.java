package com.bank.ai.gateway.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日统计实体
 *
 * <p>按天聚合关键指标，用于报表和监控。
 *
 * @since 1.0.0
 */
@Data
@TableName("daily_stats")
public class DailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate statDate;

    /** API Key ID */
    private Long apiKeyId;

    /** API Key 名称 */
    private String apiKeyName;

    /** 用户ID */
    private Long userId;

    /** 模型名 */
    private String model;

    /** 提供商 */
    private String provider;

    /** 总请求数 */
    private Integer totalRequests;

    /** 成功请求数 */
    private Integer successfulRequests;

    /** 失败请求数 */
    private Integer failedRequests;

    /** 总输入Token数 */
    private Long totalInputTokens;

    /** 总输出Token数 */
    private Long totalOutputTokens;

    /** 总延迟（毫秒） */
    private Long totalLatencyMs;

    /** 平均延迟（毫秒） */
    private Integer avgLatencyMs;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== 辅助方法 ====================

    /**
     * 计算成功率
     */
    public double getSuccessRate() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        return (successfulRequests * 100.0) / totalRequests;
    }

    /**
     * 计算平均Token数
     */
    public double getAvgTokensPerRequest() {
        if (totalRequests == null || totalRequests == 0) {
            return 0.0;
        }
        return (totalInputTokens + totalOutputTokens) / (double) totalRequests;
    }
}