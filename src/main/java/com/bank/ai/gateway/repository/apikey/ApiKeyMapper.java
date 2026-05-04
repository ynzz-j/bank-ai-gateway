package com.bank.ai.gateway.repository.apikey;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.apikey.ApiKey;
import org.apache.ibatis.annotations.Param;

/**
 * API Key Mapper
 *
 * @since 1.0.0
 */
public interface ApiKeyMapper extends BaseMapper<ApiKey> {

    /**
     * 按前缀+哈希查询有效Key
     */
    ApiKey selectByPrefixAndHash(@Param("keyPrefix") String keyPrefix,
                                       @Param("keyHash") String keyHash);

    /**
     * 按前缀查询所有非轮换状态的Key
     */
    java.util.List<ApiKey> selectByPrefix(@Param("keyPrefix") String keyPrefix);
}