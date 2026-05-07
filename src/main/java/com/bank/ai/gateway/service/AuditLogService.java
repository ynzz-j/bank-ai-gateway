package com.bank.ai.gateway.service;

import com.bank.ai.gateway.model.entity.AuditLog;
import com.bank.ai.gateway.repository.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 审计日志服务
 *
 * <p>记录所有管理操作，用于合规审计和操作追溯。
 * 只增不改不删。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 记录操作日志
     */
    public void logAction(Long userId, String username, String action,
            String resourceType, Long resourceId, String resourceName,
            Object before, Object after,
            String requestIp, String userAgent) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUsername(username);
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setResourceName(resourceName);
            auditLog.setRequestIp(requestIp);
            auditLog.setUserAgent(truncate(userAgent, 256));
            auditLog.setCreatedAt(LocalDateTime.now());

            if (before != null || after != null) {
                String snapshot = buildSnapshot(before, after);
                auditLog.setChangeSnapshot(snapshot);
            }

            auditLogMapper.insert(auditLog);
            log.info("Audit log recorded: user={}, action={}, resource={}/{}",
                    username, action, resourceType, resourceId);

        } catch (Exception e) {
            log.error("Failed to record audit log: {}", e.getMessage());
        }
    }

    public void logCreate(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            Object created, String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.CREATE.name(),
                resourceType, resourceId, resourceName,
                null, created, requestIp, userAgent);
    }

    public void logUpdate(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            Object before, Object after, String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.UPDATE.name(),
                resourceType, resourceId, resourceName,
                before, after, requestIp, userAgent);
    }

    public void logDelete(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            Object before, String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.DELETE.name(),
                resourceType, resourceId, resourceName,
                before, null, requestIp, userAgent);
    }

    public void logDisable(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.DISABLE.name(),
                resourceType, resourceId, resourceName,
                null, null, requestIp, userAgent);
    }

    public void logEnable(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.ENABLE.name(),
                resourceType, resourceId, resourceName,
                null, null, requestIp, userAgent);
    }

    public void logRotate(Long userId, String username, String resourceType, Long resourceId, String resourceName,
            String requestIp, String userAgent) {
        logAction(userId, username, AuditLog.Action.ROTATE.name(),
                resourceType, resourceId, resourceName,
                null, null, requestIp, userAgent);
    }

    /**
     * 根据ID查询
     */
    public AuditLog getById(Long id) {
        return auditLogMapper.findById(id);
    }

    /**
     * 查询用户的操作日志
     */
    public List<AuditLog> getUserLogs(Long userId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return auditLogMapper.selectByUserId(userId, startTime, endTime, limit);
    }

    /**
     * 查询资源的变更日志
     */
    public List<AuditLog> getResourceLogs(String resourceType, Long resourceId, int limit) {
        return auditLogMapper.selectByResource(resourceType, resourceId, limit);
    }

    /**
     * 分页查询
     */
    public List<AuditLog> getPage(int offset, int limit, String action, String resourceType,
                                   Long userId, LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.selectPage(offset, limit, action, resourceType, userId, startTime, endTime);
    }

    /**
     * 统计数量
     */
    public int countLogs(String action, String resourceType, Long userId,
                         LocalDateTime startTime, LocalDateTime endTime) {
        Integer count = auditLogMapper.countLogs(action, resourceType, userId, startTime, endTime);
        return count != null ? count : 0;
    }

    /**
     * 按操作类型统计
     */
    public List<Map<String, Object>> statsByAction(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.statsByAction(startTime, endTime);
    }

    /**
     * 按资源类型统计
     */
    public List<Map<String, Object>> statsByResource(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.statsByResource(startTime, endTime);
    }

    /**
     * 按用户统计
     */
    public List<Map<String, Object>> statsByUser(LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.statsByUser(startTime, endTime);
    }

    /**
     * 按多个操作类型查询
     */
    public List<AuditLog> getLogsByActions(int offset, int limit, List<String> actions,
                                            LocalDateTime startTime, LocalDateTime endTime) {
        return auditLogMapper.selectByActions(offset, limit, actions, startTime, endTime);
    }

    /**
     * 统计多个操作类型的数量
     */
    public int countLogsByActions(List<String> actions, LocalDateTime startTime, LocalDateTime endTime) {
        Integer count = auditLogMapper.countByActions(actions, startTime, endTime);
        return count != null ? count : 0;
    }

    /**
     * 导出 CSV
     */
    public byte[] exportCsv(LocalDateTime startTime, LocalDateTime endTime) {
        List<AuditLog> logs = auditLogMapper.selectPage(0, 10000, null, null, null, startTime, endTime);
        StringBuilder sb = new StringBuilder();
        sb.append("id,user_id,username,action,resource_type,resource_id,resource_name,request_ip,created_at\n");
        for (AuditLog log : logs) {
            sb.append(String.format("%d,%d,%s,%s,%s,%d,%s,%s,%s\n",
                    log.getId(),
                    log.getUserId(),
                    log.getUsername(),
                    log.getAction(),
                    log.getResourceType(),
                    log.getResourceId(),
                    log.getResourceName() != null ? log.getResourceName().replace(",", ";") : "",
                    log.getRequestIp() != null ? log.getRequestIp() : "",
                    log.getCreatedAt()));
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    // ==================== 辅助方法 ====================

    private String buildSnapshot(Object before, Object after) {
        try {
            if (before == null && after == null) {
                return null;
            }
            Map<String, Object> snapshot = new java.util.HashMap<>();
            if (before != null) {
                snapshot.put("before", objectMapper.convertValue(before, Map.class));
            }
            if (after != null) {
                snapshot.put("after", objectMapper.convertValue(after, Map.class));
            }
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Failed to build snapshot: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }
}