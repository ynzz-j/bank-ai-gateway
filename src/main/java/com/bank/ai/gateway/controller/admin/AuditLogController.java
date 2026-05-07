package com.bank.ai.gateway.controller.admin;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.common.PageResult;
import com.bank.ai.gateway.model.dto.request.AuditLogQueryRequest;
import com.bank.ai.gateway.model.entity.AuditLog;
import com.bank.ai.gateway.service.AuditLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 审计日志控制器
 *
 * <p>查询操作审计日志，用于合规审计和问题追溯。
 * 只读接口，审计日志不可修改和删除。
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * 分页查询审计日志
     */
    @GetMapping
    public Mono<ResponseEntity<PageResult<AuditLog>>> listLogs(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<AuditLog> logs = auditLogService.getPage(
                    request.getOffset(), request.getSize(),
                    request.getAction(), request.getResourceType(), request.getUserId(),
                    request.getStartDateTime(), request.getEndDateTime()
            );
            int total = auditLogService.countLogs(
                    request.getAction(), request.getResourceType(), request.getUserId(),
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(new PageResult<>(logs, total, request.getPage(), request.getSize()));
        });
    }

    /**
     * 获取单条审计日志详情
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<AuditLog>> getLogDetail(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            AuditLog log = auditLogService.getById(id);
            if (log == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "审计记录不存在");
            }
            return ResponseEntity.ok(log);
        });
    }

    /**
     * 查询指定资源的变更历史
     */
    @GetMapping("/resource/{resourceType}/{resourceId}")
    public Mono<ResponseEntity<List<AuditLog>>> getResourceHistory(
            @PathVariable String resourceType,
            @PathVariable Long resourceId,
            @RequestParam(defaultValue = "50") int limit) {
        return Mono.fromCallable(() -> {
            List<AuditLog> logs = auditLogService.getResourceLogs(resourceType, resourceId, limit);
            return ResponseEntity.ok(logs);
        });
    }

    /**
     * 查询指定用户的操作记录
     */
    @GetMapping("/user/{userId}")
    public Mono<ResponseEntity<List<AuditLog>>> getUserLogs(
            @PathVariable Long userId,
            @Valid AuditLogQueryRequest request,
            @RequestParam(defaultValue = "100") int limit) {
        return Mono.fromCallable(() -> {
            List<AuditLog> logs = auditLogService.getUserLogs(
                    userId, request.getStartDateTime(), request.getEndDateTime(), limit
            );
            return ResponseEntity.ok(logs);
        });
    }

    /**
     * 统计：按操作类型汇总
     */
    @GetMapping("/stats/by-action")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByAction(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = auditLogService.statsByAction(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 统计：按资源类型汇总
     */
    @GetMapping("/stats/by-resource")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByResource(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = auditLogService.statsByResource(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 统计：按用户汇总
     */
    @GetMapping("/stats/by-user")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByUser(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = auditLogService.statsByUser(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 敏感操作查询（用于安全审计）
     */
    @GetMapping("/sensitive")
    public Mono<ResponseEntity<PageResult<AuditLog>>> listSensitiveLogs(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            // 敏感操作：密钥轮换、删除、禁用
            List<String> sensitiveActions = List.of("DELETE", "ROTATE", "DISABLE");

            List<AuditLog> logs = auditLogService.getLogsByActions(
                    request.getOffset(), request.getSize(),
                    sensitiveActions, request.getStartDateTime(), request.getEndDateTime()
            );
            int total = auditLogService.countLogsByActions(
                    sensitiveActions, request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(new PageResult<>(logs, total, request.getPage(), request.getSize()));
        });
    }

    /**
     * 导出审计日志（CSV）- 合规归档
     */
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportLogs(@Valid AuditLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            byte[] csv = auditLogService.exportCsv(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=audit-logs.csv")
                    .body(csv);
        });
    }
}