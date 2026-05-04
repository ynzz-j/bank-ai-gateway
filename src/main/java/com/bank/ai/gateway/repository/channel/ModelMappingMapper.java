package com.bank.ai.gateway.repository.channel;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.channel.ModelMappingEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 模型映射 Mapper
 *
 * @since 1.0.0
 */
@Mapper
public interface ModelMappingMapper extends BaseMapper<ModelMappingEntity> {

    /**
     * 根据统一模型名查询所有启用的映射
     *
     * @param unifiedModel 统一模型名
     * @return 映射列表（按优先级排序）
     */
    @Select("""
            SELECT * FROM model_mapping
            WHERE unified_model = #{unifiedModel}
              AND enabled = 1
            ORDER BY priority ASC, id ASC
            """)
    List<ModelMappingEntity> findByUnifiedModel(@Param("unifiedModel") String unifiedModel);

    /**
     * 根据统一模型名和提供商查询映射
     *
     * @param unifiedModel 统一模型名
     * @param provider     提供商
     * @return 映射实体
     */
    @Select("""
            SELECT * FROM model_mapping
            WHERE unified_model = #{unifiedModel}
              AND provider = #{provider}
              AND enabled = 1
            LIMIT 1
            """)
    ModelMappingEntity findByUnifiedModelAndProvider(
            @Param("unifiedModel") String unifiedModel,
            @Param("provider") String provider
    );

    /**
     * 查询所有启用的映射
     *
     * @return 映射列表
     */
    @Select("SELECT * FROM model_mapping WHERE enabled = 1 ORDER BY unified_model, priority")
    List<ModelMappingEntity> findAllEnabled();
}
