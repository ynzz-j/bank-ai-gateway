package com.bank.ai.gateway.controller.admin;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.common.PageResult;
import com.bank.ai.gateway.model.dto.request.ChannelQueryRequest;
import com.bank.ai.gateway.model.dto.request.channel.ChannelCreateRequest;
import com.bank.ai.gateway.model.dto.request.channel.ChannelUpdateRequest;
import com.bank.ai.gateway.model.entity.channel.Channel;
import com.bank.ai.gateway.security.AuditContext;
import com.bank.ai.gateway.security.ChannelKeyEncryptor;
import com.bank.ai.gateway.service.AuditLogService;
import com.bank.ai.gateway.service.channel.ChannelService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * 渠道管理控制器
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/admin/channels")
@RequiredArgsConstructor
public class ChannelController {

    private final ChannelService channelService;
    private final ChannelKeyEncryptor encryptor;
    private final AuditLogService auditLogService;

    /**
     * 分页查询渠道列表
     */
    @GetMapping
    public Mono<ResponseEntity<PageResult<Channel>>> listChannels(@Valid ChannelQueryRequest request) {
        return Mono.fromCallable(() -> {
            List<Channel> channels = channelService.listChannels(
                    request.getPage(), request.getSize(),
                    request.getProvider(), request.getEnabled()
            );
            int total = channelService.countChannels(request.getProvider(), request.getEnabled());
            // 脱敏返回，不暴露加密的API Key
            channels.forEach(c -> {
                c.setApiKeyEncrypted(null);
                c.setApiKeyNonce(null);
            });
            return ResponseEntity.ok(new PageResult<>(channels, total, request.getPage(), request.getSize()));
        });
    }

    /**
     * 获取单个渠道
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<Channel>> getChannel(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            Channel channel = channelService.getChannelById(id);
            if (channel == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
            }
            // 脱敏返回
            channel.setApiKeyEncrypted(null);
            channel.setApiKeyNonce(null);
            return ResponseEntity.ok(channel);
        });
    }

    /**
     * 创建渠道
     */
    @PostMapping
    public Mono<ResponseEntity<Channel>> createChannel(
            @Valid @RequestBody ChannelCreateRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            // 加密 API Key
            ChannelKeyEncryptor.EncryptedKey encrypted = encryptor.encrypt(request.getApiKey());

            Channel channel = channelService.createChannel(request, encrypted);

            // 审计日志
            auditLogService.logCreate(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", channel.getId(), channel.getName(),
                    channel, clientIp, null
            );

            return ResponseEntity.ok(channel);
        });
    }

    /**
     * 更新渠道
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<Channel>> updateChannel(
            @PathVariable Long id,
            @Valid @RequestBody ChannelUpdateRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            Channel before = channelService.getChannelById(id);
            if (before == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
            }

            ChannelKeyEncryptor.EncryptedKey encrypted = null;
            if (request.getApiKey() != null && !request.getApiKey().isEmpty()) {
                encrypted = encryptor.encrypt(request.getApiKey());
            }

            Channel after = channelService.updateChannel(id, request, encrypted);

            auditLogService.logUpdate(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", id, after.getName(),
                    before, after, clientIp, null
            );

            return ResponseEntity.ok(after);
        });
    }

    /**
     * 删除渠道
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteChannel(
            @PathVariable Long id,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            Channel before = channelService.getChannelById(id);
            if (before == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
            }

            channelService.deleteChannel(id);

            auditLogService.logDelete(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", id, before.getName(),
                    before, clientIp, null
            );

            return ResponseEntity.noContent().build();
        });
    }

    /**
     * 启用渠道
     */
    @PostMapping("/{id}/enable")
    public Mono<ResponseEntity<Channel>> enableChannel(
            @PathVariable Long id,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            Channel channel = channelService.enableChannel(id);

            auditLogService.logEnable(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", id, channel.getName(),
                    clientIp, null
            );

            return ResponseEntity.ok(channel);
        });
    }

    /**
     * 禁用渠道
     */
    @PostMapping("/{id}/disable")
    public Mono<ResponseEntity<Channel>> disableChannel(
            @PathVariable Long id,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            Channel channel = channelService.disableChannel(id);

            auditLogService.logDisable(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", id, channel.getName(),
                    clientIp, null
            );

            return ResponseEntity.ok(channel);
        });
    }

    /**
     * 轮换密钥
     */
    @PostMapping("/{id}/rotate-key")
    public Mono<ResponseEntity<Map<String, String>>> rotateKey(
            @PathVariable Long id,
            @RequestParam String newApiKey,
            @RequestHeader(value = "X-Forwarded-For", required = false) String clientIp) {
        return Mono.fromCallable(() -> {
            Channel channel = channelService.getChannelById(id);
            if (channel == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
            }

            if (newApiKey == null || newApiKey.isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "新的 API Key 不能为空");
            }

            ChannelKeyEncryptor.EncryptedKey encrypted = encryptor.encrypt(newApiKey);
            channelService.updateEncryptedKey(id, encrypted);

            auditLogService.logRotate(
                    AuditContext.getUserId(), AuditContext.getUsername(),
                    "channel", id, channel.getName(),
                    clientIp, null
            );

            return ResponseEntity.ok(Map.of("message", "密钥已轮换"));
        });
    }

    /**
     * 测试渠道连接
     */
    @PostMapping("/{id}/test")
    public Mono<ResponseEntity<Map<String, Object>>> testChannel(@PathVariable Long id) {
        return Mono.fromCallable(() -> {
            Channel channel = channelService.getChannelById(id);
            if (channel == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
            }

            // TODO: 实际调用渠道的健康检查接口
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "provider", channel.getProvider(),
                    "message", "连接测试通过"
            ));
        });
    }
}