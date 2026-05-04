package com.bank.ai.gateway.service.channel;

import com.bank.ai.gateway.adapter.ModelAdapter;
import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.entity.channel.ChannelEntity;
import com.bank.ai.gateway.model.entity.channel.ModelMappingEntity;
import com.bank.ai.gateway.repository.channel.ChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 渠道路由服务
 *
 * <p>负责根据模型名选择最优渠道，支持优先级和权重路由。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelRoutingService {

    private final ChannelMapper channelMapper;
    private final ModelMappingService modelMappingService;
    private final List<ModelAdapter> adapters;
    private final Random random = new Random();

    private Map<String, ModelAdapter> adapterMap;

    /**
     * 路由结果
     */
    public record RouteResult(
            ChannelEntity channel,
            ModelAdapter adapter,
            String apiKey,
            String baseUrl,
            String actualModel
    ) {}

    /**
     * 根据模型名路由到最优渠道
     *
     * @param modelName 模型名（统一模型名或带前缀如 openai/gpt-4）
     * @return 路由结果
     */
    public RouteResult route(String modelName) {
        // 1. 解析模型名（支持 openai/gpt-4 格式）
        ModelMappingService.ModelParseResult parsed = modelMappingService.parseModel(modelName);
        String unifiedModel = parsed.unifiedModel();
        String explicitProvider = parsed.provider();

        // 2. 获取模型映射列表（用于确定可用提供商）
        List<ModelMappingEntity> modelMappings = modelMappingService.getMappings(unifiedModel);

        // 3. 如果有显式提供商前缀，直接路由到该提供商
        if (explicitProvider != null) {
            return routeToProvider(unifiedModel, explicitProvider, modelMappings);
        }

        // 4. 查询支持该统一模型的可用渠道
        List<ChannelEntity> channels = channelMapper.findAvailableByModel(unifiedModel);

        if (channels.isEmpty()) {
            log.warn("No available channel for model: {}", unifiedModel);
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到支持模型 " + unifiedModel + " 的可用渠道");
        }

        // 5. 按优先级分组
        Map<Integer, List<ChannelEntity>> priorityGroups = channels.stream()
                .collect(Collectors.groupingBy(ChannelEntity::getPriority));

        int maxPriority = priorityGroups.keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);

        List<ChannelEntity> topChannels = priorityGroups.get(maxPriority);

        // 6. 按权重随机选择
        ChannelEntity selected = selectByWeight(topChannels, modelMappings);

        // 7. 获取适配器
        ModelAdapter adapter = getAdapterMap().get(selected.getProvider());
        if (adapter == null) {
            log.error("No adapter for provider: {}", selected.getProvider());
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到 " + selected.getProvider() + " 适配器");
        }

        // 8. 获取实际模型名
        String actualModel = getActualModel(unifiedModel, selected.getProvider(), modelMappings);

        // 9. 解密 API Key（TODO: 接入 KMS）
        String apiKey = decryptApiKey(selected);

        log.info("Routed model {} to channel {} (provider={}, priority={}, weight={}, actualModel={})",
                unifiedModel, selected.getName(), selected.getProvider(),
                selected.getPriority(), selected.getWeight(), actualModel);

        return new RouteResult(
                selected,
                adapter,
                apiKey,
                selected.getBaseUrl(),
                actualModel
        );
    }

    /**
     * 路由到指定提供商
     */
    private RouteResult routeToProvider(String unifiedModel, String provider,
            List<ModelMappingEntity> modelMappings) {
        // 查询该提供商的可用渠道
        List<ChannelEntity> channels = channelMapper.findAvailableByProvider(provider);

        if (channels.isEmpty()) {
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到提供商 " + provider + " 的可用渠道");
        }

        // 选择第一个可用渠道
        ChannelEntity selected = channels.get(0);

        ModelAdapter adapter = getAdapterMap().get(provider);
        if (adapter == null) {
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到 " + provider + " 适配器");
        }

        // 获取实际模型名
        String actualModel = modelMappings.stream()
                .filter(m -> provider.equals(m.getProvider()))
                .findFirst()
                .map(ModelMappingEntity::getActualModel)
                .orElse(unifiedModel);

        String apiKey = decryptApiKey(selected);

        log.info("Routed model {} to provider {} (channel={}, actualModel={})",
                unifiedModel, provider, selected.getName(), actualModel);

        return new RouteResult(
                selected,
                adapter,
                apiKey,
                selected.getBaseUrl(),
                actualModel
        );
    }

    /**
     * 按权重随机选择渠道（考虑模型映射）
     */
    private ChannelEntity selectByWeight(List<ChannelEntity> channels,
            List<ModelMappingEntity> modelMappings) {
        // 过滤出有模型映射的渠道
        if (modelMappings != null && !modelMappings.isEmpty()) {
            Set<String> providersWithMapping = modelMappings.stream()
                    .map(ModelMappingEntity::getProvider)
                    .collect(Collectors.toSet());

            List<ChannelEntity> filteredChannels = channels.stream()
                    .filter(c -> providersWithMapping.contains(c.getProvider()))
                    .toList();

            if (!filteredChannels.isEmpty()) {
                channels = filteredChannels;
            }
        }

        if (channels.isEmpty()) {
            return null;
        }

        if (channels.size() == 1) {
            return channels.get(0);
        }

        int totalWeight = channels.stream()
                .mapToInt(c -> c.getWeight() != null ? c.getWeight() : 1)
                .sum();

        int randomValue = random.nextInt(totalWeight);
        int cumulative = 0;

        for (ChannelEntity channel : channels) {
            int weight = channel.getWeight() != null ? channel.getWeight() : 1;
            cumulative += weight;
            if (randomValue < cumulative) {
                return channel;
            }
        }

        return channels.get(0);
    }

    /**
     * 获取实际模型名
     */
    private String getActualModel(String unifiedModel, String provider,
            List<ModelMappingEntity> modelMappings) {
        // 从模型映射中获取
        if (modelMappings != null) {
            Optional<String> mapped = modelMappings.stream()
                    .filter(m -> provider.equals(m.getProvider()))
                    .map(ModelMappingEntity::getActualModel)
                    .findFirst();
            if (mapped.isPresent()) {
                return mapped.get();
            }
        }

        // 如果没有映射，使用统一模型名
        return unifiedModel;
    }

    /**
     * 获取适配器映射
     */
    private Map<String, ModelAdapter> getAdapterMap() {
        if (adapterMap == null) {
            adapterMap = adapters.stream()
                    .collect(Collectors.toMap(ModelAdapter::getProvider, Function.identity()));
        }
        return adapterMap;
    }

    /**
     * 解密 API Key
     *
     * <p>TODO: 接入 KMS 服务解密
     */
    private String decryptApiKey(ChannelEntity channel) {
        // TODO: 使用 KMS 解密
        // 目前返回明文（开发阶段）
        return channel.getApiKeyEncrypted();
    }

    /**
     * 标记渠道熔断
     *
     * @param channelId 渠道ID
     */
    public void markCircuitOpen(Long channelId) {
        ChannelEntity entity = new ChannelEntity();
        entity.setId(channelId);
        entity.setStatus((short) ChannelEntity.Status.CIRCUIT_OPEN.getCode());
        channelMapper.updateById(entity);
        log.warn("Channel {} marked as circuit open", channelId);
    }

    /**
     * 标记渠道恢复
     *
     * @param channelId 渠道ID
     */
    public void markRecovered(Long channelId) {
        ChannelEntity entity = new ChannelEntity();
        entity.setId(channelId);
        entity.setStatus((short) ChannelEntity.Status.ENABLED.getCode());
        channelMapper.updateById(entity);
        log.info("Channel {} recovered", channelId);
    }
}
