package com.bank.ai.gateway.repository;

import com.bank.ai.gateway.model.entity.channel.Channel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 渠道 Mapper
 *
 * @since 1.0.0
 */
@Mapper
public interface ChannelMapper {

    int insert(Channel channel);

    int update(Channel channel);

    int delete(@Param("id") Long id);

    Channel findById(@Param("id") Long id);

    List<Channel> selectAll();

    List<Channel> selectByProvider(@Param("provider") String provider);

    List<Channel> selectEnabled();

    List<Channel> selectPage(@Param("offset") int offset,
                             @Param("limit") int limit,
                             @Param("provider") String provider,
                             @Param("status") Short status);

    int count(@Param("provider") String provider, @Param("status") Short status);
}