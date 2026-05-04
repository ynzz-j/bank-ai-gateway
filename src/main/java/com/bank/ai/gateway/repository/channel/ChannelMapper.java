package com.bank.ai.gateway.repository.channel;

import com.bank.ai.gateway.model.entity.channel.ChannelEntity;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 渠道 Mapper
 *
 * @since 1.0.0
 */
@Mapper
public interface ChannelMapper extends BaseMapper<ChannelEntity> {

    /**
     * 查询支持指定模型的可用渠道
     *
     * @param modelName 模型名
     * @return 渠道列表（按优先级降序、权重降序）
     */
    @Select("""
        SELECT * FROM channels
        WHERE status = 1
          AND models LIKE CONCAT('%', #{modelName}, '%')
        ORDER BY priority DESC, weight DESC
        """)
    List<ChannelEntity> findAvailableByModel(@Param("modelName") String modelName);

    /**
     * 查询指定提供商的可用渠道
     *
     * @param provider 提供商标识
     * @return 渠道列表
     */
    @Select("""
        SELECT * FROM channels
        WHERE status = 1 AND provider = #{provider}
        ORDER BY priority DESC, weight DESC
        """)
    List<ChannelEntity> findAvailableByProvider(@Param("provider") String provider);

    /**
     * 查询所有可用渠道
     *
     * @return 渠道列表
     */
    @Select("""
        SELECT * FROM channels
        WHERE status = 1
        ORDER BY priority DESC, weight DESC
        """)
    List<ChannelEntity> findAllAvailable();
}
