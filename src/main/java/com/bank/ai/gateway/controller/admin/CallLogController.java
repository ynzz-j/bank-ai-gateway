package com.bank.ai.gateway.controller.admin;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.common.PageResult;
import com.bank.ai.gateway.model.dto.request.CallLogQueryRequest;
import com.bank.ai.gateway.model.entity.CallLog;
import com.bank.ai.gateway.service.CallLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 调用日志控制器
 *
 * <p>查询 AI 调用日志，用于问题排查和统计分析。
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/call-logs")
@RequiredArgsConstructor
public class CallLogController {

    private final CallLogService callLogService;

    /**
     * 分页查询调用日志
     */
    @GetMapping
    public Mono<ResponseEntity<PageResult<CallLog>>> listLogs(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<CallLog> logs = callLogService.getPage(
                    request.getOffset(), request.getSize(),
                    request.getApiKeyId(), request.getProvider(), request.getModel(),
                    request.getStartDateTime(), request.getEndDateTime()
            );
            int total = callLogService.countLogs(
                    request.getApiKeyId(), request.getProvider(), request.getModel(),
                    request.getStartDateTime(), request.getEndDateTime()
            );

            // 脱敏处理
            logs.forEach(this::maskSensitiveData);

            return ResponseEntity.ok(new PageResult<>(logs, total, request.getPage(), request.getSize()));
        });
    }

    /**
     * 获取单条调用日志详情
     */
    @GetMapping("/{requestId}")
    public Mono<ResponseEntity<CallLog>> getLogDetail(@PathVariable String requestId) {
        return Mono.fromCallable(() -> {
            CallLog log = callLogService.getByRequestId(requestId);
            if (log == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "调用记录不存在");
            }
            // 详情不脱敏（需要管理员权限）
            return ResponseEntity.ok(log);
        });
    }

    /**
     * 统计：按模型汇总
     */
    @GetMapping("/stats/by-model")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByModel(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = callLogService.statsByModel(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 统计：按渠道汇总
     */
    @GetMapping("/stats/by-channel")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByChannel(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = callLogService.statsByChannel(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 统计：按日期汇总
     */
    @GetMapping("/stats/by-date")
    public Mono<ResponseEntity<List<Map<String, Object>>>> statsByDate(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Map<String, Object>> stats = callLogService.statsByDate(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(stats);
        });
    }

    /**
     * 错误日志查询
     */
    @GetMapping("/errors")
    public Mono<ResponseEntity<PageResult<CallLog>>> listErrorLogs(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<CallLog> logs = callLogService.getErrorLogs(
                    request.getOffset(), request.getSize(),
                    request.getStartDateTime(), request.getEndDateTime()
            );
            int total = callLogService.countErrorLogs(
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok(new PageResult<>(logs, total, request.getPage(), request.getSize()));
        });
    }

    /**
     * 导出调用日志（CSV）
     */
    @GetMapping("/export")
    public Mono<ResponseEntity<byte[]>> exportLogs(@Valid CallLogQueryRequest request) {
        return Mono.fromCallable(() -> {
            byte[] csv = callLogService.exportCsv(
                    request.getApiKeyId(),
                    request.getStartDateTime(), request.getEndDateTime()
            );
            return ResponseEntity.ok()
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=call-logs.csv")
                    .body(csv);
        });
    }

    // ==================== 私有方法 ====================

    private void maskSensitiveData(CallLog log) {
        if (log.getInputContent() != null && log.getInputContent().length() > 100) {
            log.setInputContent(log.getInputContent().substring(0, 100) + "...");
        }
        if (log.getOutputContent() != null && log.getOutputContent().length() > 100) {
            log.setOutputContent(log.getOutputContent().substring(0, 100) + "...");
        }
    }
}