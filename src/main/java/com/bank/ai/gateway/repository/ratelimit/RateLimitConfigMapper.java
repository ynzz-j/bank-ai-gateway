package com.bank.ai.gateway.repository.ratelimit;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.ratelimit.RateLimitConfigEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 限流配置 Mapper
 *
 * @since 1.0.0
 */
public interface RateLimitConfigMapper extends BaseMapper<RateLimitConfigEntity> {

    /**
     * 根据维度查询配置
     *
     * @param dimension     限流维度
     * @param dimensionValue 维度值
     * @return 配置列表
     */
    RateLimitConfigEntity findByDimension(
            @Param("dimension") String dimension,
            @Param("dimensionValue") String dimensionValue
    );

    /**
     * 查询所有启用的配置
     */
    List<RateLimitConfigEntity> findAllEnabled();
}
