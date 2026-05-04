package com.bank.ai.gateway.repository.compliance;

import com.bank.ai.gateway.model.entity.ViolationLog;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 违规记录 Mapper
 */
public interface ViolationLogMapper {

    /**
     * 插入违规记录
     */
    int insert(ViolationLog entity);

    /**
     * 按API Key查询违规记录
     */
    List<ViolationLog> selectByApiKeyId(
            @Param("apiKeyId") Long apiKeyId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("limit") Integer limit
    );

    /**
     * 统计API Key违规次数
     */
    int countByApiKeyId(
            @Param("apiKeyId") Long apiKeyId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime
    );
}
