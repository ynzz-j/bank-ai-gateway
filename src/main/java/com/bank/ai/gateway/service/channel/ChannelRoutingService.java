package com.bank.ai.gateway.service.channel;

import com.bank.ai.gateway.adapter.ModelAdapter;
import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.entity.channel.ChannelEntity;
import com.bank.ai.gateway.repository.channel.ChannelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Random;
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
     * @param modelName 模型名
     * @return 路由结果
     */
    public RouteResult route(String modelName) {
        // 1. 查询支持该模型的可用渠道
        List<ChannelEntity> channels = channelMapper.findAvailableByModel(modelName);

        if (channels.isEmpty()) {
            log.warn("No available channel for model: {}", modelName);
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到支持模型 " + modelName + " 的可用渠道");
        }

        // 2. 按优先级分组
        Map<Integer, List<ChannelEntity>> priorityGroups = channels.stream()
                .collect(Collectors.groupingBy(ChannelEntity::getPriority));

        // 3. 选择最高优先级组
        int maxPriority = priorityGroups.keySet().stream()
                .max(Integer::compareTo)
                .orElse(0);

        List<ChannelEntity> topChannels = priorityGroups.get(maxPriority);

        // 4. 按权重随机选择
        ChannelEntity selected = selectByWeight(topChannels);

        // 5. 获取适配器
        ModelAdapter adapter = getAdapterMap().get(selected.getProvider());
        if (adapter == null) {
            log.error("No adapter for provider: {}", selected.getProvider());
            throw new BizException(ErrorCode.NO_AVAILABLE_CHANNEL,
                    "未找到 " + selected.getProvider() + " 适配器");
        }

        // 6. 解密 API Key（TODO: 接入 KMS）
        String apiKey = decryptApiKey(selected);

        log.info("Routed model {} to channel {} (provider={}, priority={}, weight={})",
                modelName, selected.getName(), selected.getProvider(),
                selected.getPriority(), selected.getWeight());

        return new RouteResult(
                selected,
                adapter,
                apiKey,
                selected.getBaseUrl(),
                modelName
        );
    }

    /**
     * 按权重随机选择渠道
     */
    private ChannelEntity selectByWeight(List<ChannelEntity> channels) {
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
