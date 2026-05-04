package com.bank.ai.gateway.model.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 渠道实体
 *
 * @since 1.0.0
 */
@Data
@TableName("channels")
public class ChannelEntity {

    /**
     * 渠道状态枚举
     */
    public enum Status {
        ENABLED(1, "启用"),
        CIRCUIT_OPEN(2, "熔断"),
        UNAVAILABLE(3, "不可用");

        private final int code;
        private final String desc;

        Status(int code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        public int getCode() {
            return code;
        }

        public static Status fromCode(int code) {
            for (Status status : values()) {
                if (status.code == code) {
                    return status;
                }
            }
            return UNAVAILABLE;
        }
    }

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 渠道名称
     */
    private String name;

    /**
     * 服务商标识（OPENAI/CLAUDE/BAIDU等）
     */
    private String provider;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * AES-256-GCM加密后的API密钥
     */
    private String apiKeyEncrypted;

    /**
     * GCM加密的nonce值
     */
    private String apiKeyNonce;

    /**
     * KMS密钥版本号
     */
    private String apiKeyKmsVersion;

    /**
     * 支持的模型列表（JSON数组）
     */
    private String models;

    /**
     * 优先级（越大越优先）
     */
    private Integer priority;

    /**
     * 权重（同优先级按权重分配）
     */
    private Integer weight;

    /**
     * 状态：1启用 2熔断 3不可用
     */
    private Short status;

    /**
     * 最后健康检查时间
     */
    private LocalDateTime lastHealthCheck;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    // ==================== 辅助方法 ====================

    /**
     * 获取模型列表
     */
    public List<String> getModelList() {
        if (models == null || models.isBlank()) {
            return List.of();
        }
        try {
            // 简单解析 JSON 数组
            String json = models.trim();
            if (json.startsWith("[") && json.endsWith("]")) {
                json = json.substring(1, json.length() - 1);
                return List.of(json.split(","))
                        .stream()
                        .map(s -> s.trim().replace("\"", ""))
                        .filter(s -> !s.isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            // ignore
        }
        return List.of();
    }

    /**
     * 是否支持指定模型
     */
    public boolean supportsModel(String modelName) {
        return getModelList().contains(modelName);
    }

    /**
     * 是否可用
     */
    public boolean isAvailable() {
        return status != null && status == Status.ENABLED.getCode();
    }
}
