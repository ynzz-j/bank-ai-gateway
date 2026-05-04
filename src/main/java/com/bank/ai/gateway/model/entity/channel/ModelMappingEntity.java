package com.bank.ai.gateway.model.entity.channel;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型映射实体
 *
 * <p>存储统一模型名到各渠道实际模型名的映射关系。
 * 例如：统一模型 "gpt-4" → OpenAI 实际模型 "gpt-4-turbo"
 *
 * @since 1.0.0
 */
@Data
@TableName("model_mapping")
public class ModelMappingEntity {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 统一模型名（用户请求的模型名）
     */
    private String unifiedModel;

    /**
     * 渠道提供商（OPENAI/CLAUDE/BAIDU/QWEN/ZHIPU/KIMI）
     */
    private String provider;

    /**
     * 渠道实际模型名
     */
    private String actualModel;

    /**
     * 优先级（同统一模型下，数字越小优先级越高）
     */
    private Integer priority;

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
     * 是否启用
     */
    public boolean isEnabled() {
        return enabled != null && enabled == 1;
    }
}
