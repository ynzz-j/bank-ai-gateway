package com.bank.ai.gateway.service.channel;

import com.bank.ai.gateway.model.entity.channel.ModelMapping;
import com.bank.ai.gateway.repository.channel.ModelMappingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型映射服务
 *
 * <p>管理统一模型名到各渠道实际模型名的映射关系。
 * 例如：用户请求 "gpt-4" → 路由到 OpenAI 使用 "gpt-4-turbo"
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelMappingService {

    private final ModelMappingMapper modelMappingMapper;

    /**
     * 根据统一模型名获取所有启用的映射（按优先级排序）
     *
     * @param unifiedModel 统一模型名
     * @return 映射列表
     */
    public List<ModelMapping> getMappings(String unifiedModel) {
        return modelMappingMapper.findByUnifiedModel(unifiedModel);
    }

    /**
     * 根据统一模型名和提供商获取实际模型名
     *
     * @param unifiedModel 统一模型名
     * @param provider     提供商（OPENAI/CLAUDE/BAIDU等）
     * @return 实际模型名，如果未找到返回 null
     */
    public String getActualModel(String unifiedModel, String provider) {
        ModelMapping mapping = modelMappingMapper.findByUnifiedModelAndProvider(
                unifiedModel, provider);
        if (mapping == null) {
            log.warn("No model mapping for unifiedModel={}, provider={}", unifiedModel, provider);
            return null;
        }
        return mapping.getActualModel();
    }

    /**
     * 获取所有启用的映射
     *
     * @return 映射列表
     */
    public List<ModelMapping> getAllMappings() {
        return modelMappingMapper.findAllEnabled();
    }

    /**
     * 解析请求中的模型名
     *
     * <p>支持两种格式：
     * 1. 统一模型名：gpt-4 → 使用模型映射
     * 2. 带提供商前缀：openai/gpt-4-turbo → 直接使用，不走映射
     *
     * @param modelName 用户请求的模型名
     * @return 解析结果（unifiedModel, provider, actualModel）
     */
    public ModelParseResult parseModel(String modelName) {
        // 检查是否带提供商前缀（如 openai/gpt-4-turbo）
        int slashIndex = modelName.indexOf('/');
        if (slashIndex > 0) {
            String provider = modelName.substring(0, slashIndex).toUpperCase();
            String actualModel = modelName.substring(slashIndex + 1);
            return new ModelParseResult(modelName, provider, actualModel);
        }

        // 不带前缀，作为统一模型名处理
        return new ModelParseResult(modelName, null, null);
    }

    /**
     * 模型解析结果
     *
     * @param unifiedModel 统一模型名
     * @param provider     提供商（可能为空）
     * @param actualModel  实际模型名（可能为空）
     */
    public record ModelParseResult(
            String unifiedModel,
            String provider,
            String actualModel
    ) {}
}
