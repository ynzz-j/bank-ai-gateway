package com.bank.ai.gateway.controller;

import com.bank.ai.gateway.common.ApiResponse;
import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.compliance.ContentFilter.FilterContext;
import com.bank.ai.gateway.model.dto.request.ChatRequest;
import com.bank.ai.gateway.model.dto.response.ChatResponse;
import com.bank.ai.gateway.model.entity.apikey.ApiKey;
import com.bank.ai.gateway.service.ChatService;
import com.bank.ai.gateway.service.ComplianceBlockedException;
import com.bank.ai.gateway.service.apikey.ApiKeyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 聊天补全接口
 *
 * <p>OpenAI 兼容接口，支持流式与非流式。
 *
 * <p>认证方式：
 * <ul>
 *   <li>API Key: Authorization: Bearer bgk_xxx</li>
 *   <li>JWT Token: Authorization: Bearer eyJxxx（管理后台）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ApiKeyService apiKeyService;

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String API_KEY_PREFIX = "bgk_";

    /**
     * 聊天补全
     *
     * @param request 请求
     * @param httpRequest HTTP 请求
     * @return 响应
     */
    @PostMapping("/chat/completions")
    public Mono<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest request,
            ServerHttpRequest httpRequest) {

        return validateApiKey(httpRequest)
                .flatMap(apiKey -> {
                    log.info("聊天补全请求: apiKeyId={}, model={}, stream={}\n",
                            apiKey.getId(), request.getModel(), request.getStream());

                    FilterContext context = createFilterContext(apiKey);

                    return chatService.chat(request, context)
                            .doOnSuccess(response -> {
                                log.info("聊天补全成功: apiKeyId={}, model={}", apiKey.getId(), request.getModel());
                            })
                            .doOnError(ComplianceBlockedException.class, e -> {
                                log.warn("合规拦截: apiKeyId={}, violation={}", apiKey.getId(), e.getViolation());
                            })
                            .doOnError(e -> {
                                if (!(e instanceof ComplianceBlockedException)) {
                                    log.error("聊天补全失败: apiKeyId={}, error={}", apiKey.getId(), e.getMessage());
                                }
                            });
                })
                .map(ApiResponse::success);
    }

    /**
     * 聊天补全（流式）
     *
     * @param request 请求
     * @param httpRequest HTTP 请求
     * @return SSE 数据流
     */
    @PostMapping(value = "/chat/completions/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(
            @Valid @RequestBody ChatRequest request,
            ServerHttpRequest httpRequest) {

        return validateApiKey(httpRequest)
                .flatMapMany(apiKey -> {
                    log.info("聊天补全请求（流式）: apiKeyId={}, model={}", apiKey.getId(), request.getModel());

                    request.setStream(true);
                    FilterContext context = createFilterContext(apiKey);

                    return chatService.chatStream(request, context)
                            .map(data -> ServerSentEvent.<String>builder()
                                    .data(data)
                                    .build())
                            .doOnComplete(() -> log.info("聊天补全流式完成: apiKeyId={}, model={}",
                                    apiKey.getId(), request.getModel()))
                            .doOnError(ComplianceBlockedException.class, e -> {
                                log.warn("合规拦截（流式）: apiKeyId={}, violation={}", apiKey.getId(), e.getViolation());
                            })
                            .doOnError(e -> {
                                if (!(e instanceof ComplianceBlockedException)) {
                                    log.error("聊天补全流式失败: apiKeyId={}, error={}", apiKey.getId(), e.getMessage());
                                }
                            });
                });
    }

    /**
     * 验证 API Key
     *
     * @param httpRequest HTTP 请求
     * @return API Key 实体
     */
    private Mono<ApiKey> validateApiKey(ServerHttpRequest httpRequest) {
        String authHeader = httpRequest.getHeaders().getFirst("Authorization");

        if (authHeader == null || authHeader.isEmpty()) {
            return Mono.error(new BizException(ErrorCode.UNAUTHORIZED, "缺少认证信息"));
        }

        // Bearer Token 格式
        if (authHeader.startsWith(BEARER_PREFIX)) {
            String token = authHeader.substring(BEARER_PREFIX.length());

            // API Key 认证
            if (token.startsWith(API_KEY_PREFIX)) {
                try {
                    ApiKey apiKey = apiKeyService.validate(token);
                    if (!apiKey.isActive()) {
                        return Mono.error(new BizException(ErrorCode.API_KEY_DISABLED,
                                "API Key 已禁用"));
                    }
                    return Mono.just(apiKey);
                } catch (BizException e) {
                    return Mono.error(e);
                }
            }

            // JWT Token 认证（管理后台）
            // TODO: 集成 JWT 验证
            return Mono.error(new BizException(ErrorCode.UNAUTHORIZED, "暂不支持 JWT 认证"));
        }

        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED, "无效的认证格式"));
    }

    /**
     * 创建合规过滤上下文
     */
    private FilterContext createFilterContext(ApiKey apiKey) {
        FilterContext context = new FilterContext();
        context.setApiKeyId(apiKey.getId());
        context.setUserId(apiKey.getUserId());
        return context;
    }
}
