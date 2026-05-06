package com.bank.ai.gateway.service;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.bank.ai.gateway.model.entity.CallLog;
import com.bank.ai.gateway.model.entity.channel.Channel;
import com.bank.ai.gateway.repository.CallLogMapper;
import com.bank.ai.gateway.service.channel.ChannelRoutingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 调用日志服务
 *
 * <p>记录每次 AI 调用的完整明细，用于审计和统计。
 * 支持异步写入，不阻塞主请求流程。
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallLogService {

    private final CallLogMapper callLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构建调用日志（从路由结果和响应中提取）
     *
     * @param requestId 请求ID
     * @param apiKeyId API Key ID
     * @param apiKeyName API Key 名称
     * @param userId 用户ID
     * @param request 请求
     * @param route 路由结果
     * @param response 响应
     * @param latencyMs 延迟（毫秒）
     * @param clientIp 客户端IP
     * @param userAgent User-Agent
     * @return 调用日志
     */
    public CallLog buildCallLog(String requestId, Long apiKeyId, String apiKeyName, Long userId,
            ChatRequest request, ChannelRoutingService.RouteResult route,
            ChatResponse response, long latencyMs,
            String clientIp, String userAgent) {
        
        CallLog callLog = new CallLog();
        callLog.setRequestId(requestId);
        callLog.setApiKeyId(apiKeyId);
        callLog.setApiKeyName(apiKeyName);
        callLog.setUserId(userId);
        
        // 模型信息
        callLog.setModel(request.getModel());
        callLog.setProvider(route.channel().getProvider());
        callLog.setActualModel(route.actualModel());
        callLog.setChannelId(route.channel().getId());
        callLog.setChannelName(route.channel().getName());
        
        // 请求内容（截断）
        callLog.setInputContent(truncateContent(extractInputContent(request), 2000));
        
        // 响应内容
        if (response != null) {
            callLog.setOutputContent(truncateContent(extractOutputContent(response), 2000));
            
            // Token统计
            if (response.getUsage() != null) {
                callLog.setInputTokens(response.getUsage().getPromptTokens());
                callLog.setOutputTokens(response.getUsage().getCompletionTokens());
                callLog.setTotalTokens(response.getUsage().getTotalTokens());
            }
            
            // 结束原因
            if (response.getChoices() != null && !response.getChoices().isEmpty()) {
                callLog.setFinishReason(response.getChoices().get(0).getFinishReason());
            }
        }
        
        // 性能指标
        callLog.setLatencyMs((int) latencyMs);
        
        // 上下文
        callLog.setClientIp(clientIp);
        callLog.setUserAgent(truncate(userAgent, 256));
        callLog.setCreatedAt(LocalDateTime.now());
        
        return callLog;
    }

    /**
     * 构建错误日志
     */
    public CallLog buildErrorLog(String requestId, Long apiKeyId, String apiKeyName, Long userId,
            ChatRequest request, ChannelRoutingService.RouteResult route,
            String errorCode, String errorMessage, long latencyMs,
            String clientIp, String userAgent) {
        
        CallLog callLog = buildCallLog(requestId, apiKeyId, apiKeyName, userId, request, route, null, latencyMs, clientIp, userAgent);
        callLog.setErrorCode(errorCode);
        callLog.setErrorMessage(truncate(errorMessage, 500));
        return callLog;
    }

    /**
     * 异步写入日志（不阻塞主流程）
     */
    @Async
    public void writeLogAsync(CallLog callLog) {
        try {
            callLogMapper.insert(callLog);
            log.debug("Call log written: requestId={}", callLog.getRequestId());
        } catch (Exception e) {
            log.error("Failed to write call log: {}", e.getMessage());
        }
    }

    /**
     * 同步写入日志（用于需要确保记录的场景）
     */
    public void writeLog(CallLog callLog) {
        callLogMapper.insert(callLog);
        log.debug("Call log written: requestId={}", callLog.getRequestId());
    }

    /**
     * 生成请求ID
     */
    public String generateRequestId() {
        return "req-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
    }

    /**
     * 查询最近调用日志
     */
    public List<CallLog> getRecentLogs(int limit) {
        return callLogMapper.selectRecent(limit);
    }

    /**
     * 根据请求ID查询
     */
    public CallLog getByRequestId(String requestId) {
        return callLogMapper.findByRequestId(requestId);
    }

    /**
     * 查询API Key的调用日志
     */
    public List<CallLog> getLogsByApiKey(Long apiKeyId, LocalDateTime startTime, LocalDateTime endTime, int limit) {
        return callLogMapper.selectByApiKeyId(apiKeyId, startTime, endTime, limit);
    }

    /**
     * 分页查询
     */
    public List<CallLog> getPage(int offset, int limit, Long apiKeyId, String provider, String model,
                                  LocalDateTime startTime, LocalDateTime endTime) {
        return callLogMapper.selectPage(offset, limit, apiKeyId, provider, model, startTime, endTime);
    }

    /**
     * 统计数量
     */
    public int countLogs(Long apiKeyId, String provider, String model,
                          LocalDateTime startTime, LocalDateTime endTime) {
        Integer count = callLogMapper.countLogs(apiKeyId, provider, model, startTime, endTime);
        return count != null ? count : 0;
    }

    /**
     * 按模型统计
     */
    public List<java.util.Map<String, Object>> statsByModel(LocalDateTime startTime, LocalDateTime endTime) {
        return callLogMapper.statsByModel(startTime, endTime);
    }

    /**
     * 按渠道统计
     */
    public List<java.util.Map<String, Object>> statsByChannel(LocalDateTime startTime, LocalDateTime endTime) {
        return callLogMapper.statsByChannel(startTime, endTime);
    }

    /**
     * 按日期统计
     */
    public List<java.util.Map<String, Object>> statsByDate(LocalDateTime startTime, LocalDateTime endTime) {
        return callLogMapper.statsByDate(startTime, endTime);
    }

    /**
     * 查询错误日志
     */
    public List<CallLog> getErrorLogs(int offset, int limit, LocalDateTime startTime, LocalDateTime endTime) {
        return callLogMapper.selectErrors(offset, limit, startTime, endTime);
    }

    /**
     * 统计错误数量
     */
    public int countErrorLogs(LocalDateTime startTime, LocalDateTime endTime) {
        Integer count = callLogMapper.countErrors(startTime, endTime);
        return count != null ? count : 0;
    }

    /**
     * 导出 CSV
     */
    public byte[] exportCsv(Long apiKeyId, LocalDateTime startTime, LocalDateTime endTime) {
        List<CallLog> logs = callLogMapper.selectByApiKeyId(apiKeyId, startTime, endTime, 10000);
        StringBuilder sb = new StringBuilder();
        sb.append("request_id,api_key,model,provider,channel,latency_ms,total_tokens,created_at,error_code\n");
        for (CallLog log : logs) {
            sb.append(String.format("%s,%s,%s,%s,%s,%d,%d,%s,%s\n",
                    log.getRequestId(),
                    log.getApiKeyName(),
                    log.getModel(),
                    log.getProvider(),
                    log.getChannelName(),
                    log.getLatencyMs() != null ? log.getLatencyMs() : 0,
                    log.getTotalTokens() != null ? log.getTotalTokens() : 0,
                    log.getCreatedAt(),
                    log.getErrorCode() != null ? log.getErrorCode() : ""));
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    // ==================== 辅助方法 ====================

    private String extractInputContent(ChatRequest request) {
        if (request.getMessages() == null || request.getMessages().isEmpty()) {
            return "";
        }
        return request.getMessages().stream()
                .map(m -> m.getRole() + ": " + m.getContent())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    private String extractOutputContent(ChatResponse response) {
        if (response.getChoices() == null || response.getChoices().isEmpty()) {
            return "";
        }
        return response.getChoices().stream()
                .filter(c -> c.getMessage() != null)
                .map(c -> c.getMessage().getContent())
                .reduce("", (a, b) -> a + "\n" + b);
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...[truncated]";
    }

    private String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        }
        return str.substring(0, maxLength);
    }
}