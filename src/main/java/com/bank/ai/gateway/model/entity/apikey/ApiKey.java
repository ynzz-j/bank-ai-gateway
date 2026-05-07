package com.bank.ai.gateway.model.entity.apikey;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * API Key 实体
 *
 * <p>存储结构：key_prefix（明文前缀）+ key_hash（SHA256完整哈希）
 * - 前缀用于快速定位（LIKE 'bgk_%'）
 * - 完整Key的哈希用于验证，防泄露
 *
 * @since 1.0.0
 */
@Data
@TableName("api_keys")
public class ApiKey {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** Key前缀（明文），用于快速定位 */
    private String keyPrefix;

    /** 完整Key的SHA256哈希 */
    private String keyHash;

    /** 所属用户ID */
    private Long userId;

    /** Key名称 */
    private String name;

    /** 每分钟请求数配额，-1表示无限制 */
    private Integer quotaRpm;

    /** 每分钟Token数配额，-1表示无限制 */
    private Integer quotaTpm;

    /** 总配额(Token数)，-1表示无限制 */
    private Long quotaTotal;

    /** 已用配额 */
    private Long usedQuota;

    /** 状态: 1启用 0禁用 2已轮换 */
    private Integer status;

    /** 过期时间，null表示永不过期 */
    private LocalDateTime expiresAt;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== 辅助方法 ====================

    /**
     * 是否启用状态
     */
    public boolean isActive() {
        return status != null && status == 1;
    }
}