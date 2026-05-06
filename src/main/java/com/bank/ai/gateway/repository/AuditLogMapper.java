package com.bank.ai.gateway.repository;

import com.bank.ai.gateway.model.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志 Mapper
 *
 * @since 1.0.0
 */
@Mapper
public interface AuditLogMapper {

    /**
     * 插入审计日志
     */
    int insert(AuditLog auditLog);

    /**
     * 根据ID查询
     */
    AuditLog findById(@Param("id") Long id);

    /**
     * 查询用户操作日志
     */
    List<AuditLog> selectByUserId(@Param("userId") Long userId,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime,
                                   @Param("limit") int limit);

    /**
     * 查询资源变更日志
     */
    List<AuditLog> selectByResource(@Param("resourceType") String resourceType,
                                    @Param("resourceId") Long resourceId,
                                    @Param("limit") int limit);

    /**
     * 分页查询
     */
    List<AuditLog> selectPage(@Param("offset") int offset,
                              @Param("limit") int limit,
                              @Param("action") String action,
                              @Param("resourceType") String resourceType,
                              @Param("userId") Long userId,
                              @Param("startTime") LocalDateTime startTime,
                              @Param("endTime") LocalDateTime endTime);

    /**
     * 统计数量
     */
    Integer countLogs(@Param("action") String action,
                      @Param("resourceType") String resourceType,
                      @Param("userId") Long userId,
                      @Param("startTime") LocalDateTime startTime,
                      @Param("endTime") LocalDateTime endTime);

    /**
     * 按操作类型统计
     */
    List<Map<String, Object>> statsByAction(@Param("startTime") LocalDateTime startTime,
                                             @Param("endTime") LocalDateTime endTime);

    /**
     * 按资源类型统计
     */
    List<Map<String, Object>> statsByResource(@Param("startTime") LocalDateTime startTime,
                                               @Param("endTime") LocalDateTime endTime);

    /**
     * 按用户统计
     */
    List<Map<String, Object>> statsByUser(@Param("startTime") LocalDateTime startTime,
                                          @Param("endTime") LocalDateTime endTime);

    /**
     * 按多个操作类型查询
     */
    List<AuditLog> selectByActions(@Param("offset") int offset,
                                   @Param("limit") int limit,
                                   @Param("actions") List<String> actions,
                                   @Param("startTime") LocalDateTime startTime,
                                   @Param("endTime") LocalDateTime endTime);

    /**
     * 统计多个操作类型的数量
     */
    Integer countByActions(@Param("actions") List<String> actions,
                           @Param("startTime") LocalDateTime startTime,
                           @Param("endTime") LocalDateTime endTime);
}