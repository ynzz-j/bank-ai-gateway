package com.bank.ai.gateway.repository.channel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.channel.ModelMapping;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 模型映射 Mapper
 *
 * @since 1.0.0
 */
public interface ModelMappingMapper extends BaseMapper<ModelMapping> {

    /**
     * 根据统一模型名查询所有启用的映射
     *
     * @param unifiedModel 统一模型名
     * @return 映射列表（按优先级排序）
     */
    List<ModelMapping> findByUnifiedModel(@Param("unifiedModel") String unifiedModel);

    /**
     * 根据统一模型名和提供商查询映射
     *
     * @param unifiedModel 统一模型名
     * @param provider     提供商
     * @return 映射实体
     */
    ModelMapping findByUnifiedModelAndProvider(
            @Param("unifiedModel") String unifiedModel,
            @Param("provider") String provider
    );

    /**
     * 查询所有启用的映射
     *
     * @return 映射列表
     */
    List<ModelMapping> findAllEnabled();
}
