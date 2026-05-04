package com.bank.ai.gateway.repository.channel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.channel.ChannelEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 渠道 Mapper
 *
 * @since 1.0.0
 */
public interface ChannelMapper extends BaseMapper<ChannelEntity> {

    /**
     * 查询支持指定模型的可用渠道
     *
     * @param modelName 模型名
     * @return 渠道列表（按优先级降序、权重降序）
     */
    List<ChannelEntity> findAvailableByModel(@Param("modelName") String modelName);

    /**
     * 查询指定提供商的可用渠道
     *
     * @param provider 提供商标识
     * @return 渠道列表
     */
    List<ChannelEntity> findAvailableByProvider(@Param("provider") String provider);

    /**
     * 查询所有可用渠道
     *
     * @return 渠道列表
     */
    List<ChannelEntity> findAllAvailable();
}
