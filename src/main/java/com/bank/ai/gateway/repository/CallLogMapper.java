package com.bank.ai.gateway.repository;

import com.bank.ai.gateway.model.entity.CallLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 调用日志 Mapper
 *
 * @since 1.0.0
 */
@Mapper
public interface CallLogMapper {

    /**
     * 插入调用日志
     */
    int insert(CallLog callLog);

    /**
     * 根据请求ID查询
     */
    CallLog findByRequestId(@Param("requestId") String requestId);

    /**
     * 查询最近日志（分页）
     */
    List<CallLog> selectRecent(@Param("limit") int limit);

    /**
     * 查询API Key的调用日志
     */
    List<CallLog> selectByApiKeyId(@Param("apiKeyId") Long apiKeyId,
                                    @Param("startTime") LocalDateTime startTime,
                                    @Param("endTime") LocalDateTime endTime,
                                    @Param("limit") int limit);

    /**
     * 分页查询
     */
    List<CallLog> selectPage(@Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("apiKeyId") Long apiKeyId,
                              @Param("provider") String provider,
                              @Param("model") String model,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 统计数量
     */
    Integer countLogs(@Param("apiKeyId") Long apiKeyId,
                      @Param("provider") String provider,
                      @Param("model") String model,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按模型统计
     */
    List<Map<String, Object>> statsByModel(@Param("startTime") LocalDateTime startTime,
                                           @Param("endTime") LocalDateTime endTime);

    /**
     * 按渠道统计
     */
    List<Map<String, Object>> statsByChannel(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按日期统计
     */
    List<Map<String, Object>> statsByDate(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 查询错误日志
     */
    List<CallLog> selectErrors(@Param("offset") int offset,
                               @Param("limit") int limit,
                               @Param("startTime") LocalDateTime startTime,
                               @Param("endTime") LocalDateTime endTime);

    /**
     * 统计错误数量
     */
    Integer countErrors(@Param("startTime") LocalDateTime startTime,
                        @Param("endTime") LocalDateTime endTime);
}