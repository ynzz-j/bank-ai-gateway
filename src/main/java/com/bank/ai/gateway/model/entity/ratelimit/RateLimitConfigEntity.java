package com.bank.ai.gateway.model.entity.ratelimit;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 限流配置实体
 *
 * <p>存储 API Key、用户、IP 等维度的限流配置。
 *
 * @since 1.0.0
 */
@Data
@TableName("rate_limit_config")
public class RateLimitConfigEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 限流维度（API_KEY, USER, IP）
     */
    private String dimension;

    /**
     * 维度值（API Key ID、用户 ID、IP 地址）
     */
    private String dimensionValue;

    /**
     * 限制配置（格式：100/min, 1000/hour）
     */
    private String limitConfig;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Short enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 限流维度枚举
     */
    public enum Dimension {
        API_KEY("API_KEY"),
        USER("USER"),
        IP("IP");

        private final String value;

        Dimension(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
}
