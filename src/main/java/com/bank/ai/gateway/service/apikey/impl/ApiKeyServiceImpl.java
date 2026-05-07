package com.bank.ai.gateway.service.apikey.impl;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.apikey.CreateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.RotateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.UpdateApiKeyRequest;
import com.bank.ai.gateway.model.dto.response.apikey.ApiKeyResponse;
import com.bank.ai.gateway.model.dto.response.apikey.CreateApiKeyResponse;
import com.bank.ai.gateway.model.entity.apikey.ApiKey;
import com.bank.ai.gateway.repository.apikey.ApiKeyMapper;
import com.bank.ai.gateway.service.apikey.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Random;

/**
 * API Key Service 实现
 *
 * <p>安全设计：
 * <ul>
 *   <li>Key结构：4位前缀(bgk_) + 24位随机字符 = 28位完整Key</li>
 *   <li>存储：前缀明文 + SHA256哈希（不存完整Key）</li>
 *   <li>验证：解析前缀 → 查库 → 重新计算哈希比对</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@Service
public class ApiKeyServiceImpl implements ApiKeyService {

    private static final String KEY_PREFIX = "bgk_";
    private static final int RANDOM_LENGTH = 24;
    private static final char[] CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private ApiKeyMapper apiKeyMapper;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private final Random random = new Random();

    // ==================== Key 生成与哈希 ====================

    /**
     * 生成完整 API Key
     * 格式：bgk_ + 24位随机字符
     */
    private String generateApiKey() {
        StringBuilder sb = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(CHARS[random.nextInt(CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * 计算 API Key 的 SHA256 哈希
     */
    private String sha256(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * 从完整 Key 提取前缀（前4位）
     */
    private String extractPrefix(String apiKey) {
        if (apiKey == null || apiKey.length() < 4) return null;
        return apiKey.substring(0, 4);
    }

    // ==================== 业务方法 ====================

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateApiKeyResponse create(Long userId, CreateApiKeyRequest request) {
        // 生成Key
        String fullKey = generateApiKey();
        String prefix = extractPrefix(fullKey);
        String hash = sha256(fullKey);

        // 构建实体
        ApiKey entity = new ApiKey();
        entity.setKeyPrefix(prefix);
        entity.setKeyHash(hash);
        entity.setUserId(userId);
        entity.setName(request.getName());
        entity.setQuotaRpm(request.getQuotaRpm() != null ? request.getQuotaRpm() : 60);
        entity.setQuotaTpm(request.getQuotaTpm() != null ? request.getQuotaTpm() : 100000);
        entity.setQuotaTotal(request.getQuotaTotal() != null ? request.getQuotaTotal() : -1L);
        entity.setUsedQuota(0L);
        entity.setStatus(1); // 启用

        if (request.getExpiresAt() != null && !request.getExpiresAt().isEmpty()) {
            entity.setExpiresAt(LocalDateTime.parse(request.getExpiresAt(), FORMATTER));
        }

        apiKeyMapper.insert(entity);

        // 构建响应（包含完整Key，仅此一次）
        CreateApiKeyResponse response = new CreateApiKeyResponse();
        response.setId(entity.getId());
        response.setApiKey(fullKey);
        response.setKeyPrefix(prefix);
        response.setName(entity.getName());
        response.setQuotaRpm(entity.getQuotaRpm());
        response.setQuotaTpm(entity.getQuotaTpm());
        response.setQuotaTotal(entity.getQuotaTotal());
        response.setCreatedAt(FORMATTER.format(entity.getCreatedAt()));
        if (entity.getExpiresAt() != null) {
            response.setExpiresAt(FORMATTER.format(entity.getExpiresAt()));
        }

        log.info("API Key created: id={}, prefix={}, userId={}", entity.getId(), prefix, userId);
        return response;
    }

    @Override
    public List<ApiKeyResponse> listByUser(Long userId) {
        List<ApiKey> entities = apiKeyMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ApiKey>()
                        .eq(ApiKey::getUserId, userId)
                        .ne(ApiKey::getStatus, 2) // 排除已轮换
                        .orderByDesc(ApiKey::getCreatedAt)
        );

        List<ApiKeyResponse> result = new ArrayList<>();
        for (ApiKey e : entities) {
            result.add(toResponse(e));
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public ApiKeyResponse update(Long userId, Long keyId, UpdateApiKeyRequest request) {
        ApiKey entity = getAndValidateOwnership(userId, keyId);

        if (request.getName() != null) entity.setName(request.getName());
        if (request.getQuotaRpm() != null) entity.setQuotaRpm(request.getQuotaRpm());
        if (request.getQuotaTpm() != null) entity.setQuotaTpm(request.getQuotaTpm());
        if (request.getQuotaTotal() != null) entity.setQuotaTotal(request.getQuotaTotal());
        if (request.getStatus() != null) entity.setStatus(request.getStatus());
        if (request.getExpiresAt() != null && !request.getExpiresAt().isEmpty()) {
            entity.setExpiresAt(LocalDateTime.parse(request.getExpiresAt(), FORMATTER));
        }

        apiKeyMapper.updateById(entity);
        return toResponse(entity);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void delete(Long userId, Long keyId) {
        ApiKey entity = getAndValidateOwnership(userId, keyId);
        // 物理删除（也可以使用逻辑删除，这里用物理删除）
        apiKeyMapper.deleteById(keyId);
        log.info("API Key deleted: id={}, prefix={}", keyId, entity.getKeyPrefix());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void disable(Long userId, Long keyId) {
        ApiKey entity = getAndValidateOwnership(userId, keyId);
        entity.setStatus(0);
        apiKeyMapper.updateById(entity);
        log.info("API Key disabled: id={}, prefix={}", keyId, entity.getKeyPrefix());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void enable(Long userId, Long keyId) {
        ApiKey entity = getAndValidateOwnership(userId, keyId);
        entity.setStatus(1);
        apiKeyMapper.updateById(entity);
        log.info("API Key enabled: id={}, prefix={}", keyId, entity.getKeyPrefix());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public CreateApiKeyResponse rotate(Long userId, Long keyId, RotateApiKeyRequest request) {
        ApiKey oldKey = getAndValidateOwnership(userId, keyId);

        // 标记旧Key为已轮换
        oldKey.setStatus(2);
        apiKeyMapper.updateById(oldKey);

        // 创建新Key（继承配额）
        CreateApiKeyRequest createReq = new CreateApiKeyRequest();
        createReq.setName(oldKey.getName() + " (rotated)");
        createReq.setQuotaRpm(request.getQuotaRpm() != null ? request.getQuotaRpm() : oldKey.getQuotaRpm());
        createReq.setQuotaTpm(request.getQuotaTpm() != null ? request.getQuotaTpm() : oldKey.getQuotaTpm());
        createReq.setQuotaTotal(request.getQuotaTotal() != null ? request.getQuotaTotal() : oldKey.getQuotaTotal());

        CreateApiKeyResponse response = create(userId, createReq);
        log.info("API Key rotated: oldId={}, newId={}, userId={}", keyId, response.getId(), userId);
        return response;
    }

    @Override
    public ApiKey validate(String apiKey) {
        String prefix = extractPrefix(apiKey);
        if (prefix == null) return null;

        String hash = sha256(apiKey);
        ApiKey entity = apiKeyMapper.selectByPrefixAndHash(prefix, hash);

        if (entity == null) return null;

        // 检查状态
        if (entity.getStatus() == 0) {
            throw new BizException(ErrorCode.API_KEY_DISABLED);
        }
        if (entity.getStatus() == 2) {
            throw new BizException(ErrorCode.API_KEY_REVOKED);
        }

        // 检查过期
        if (entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.API_KEY_EXPIRED);
        }

        return entity;
    }

    @Override
    public boolean checkQuota(Long keyId, int tokens) {
        // TODO: 使用Redis Lua脚本进行RPM/TPM检查
        // 暂时返回true，后续集成Redis限流
        return true;
    }

    @Override
    public void consumeQuota(Long keyId, int tokens) {
        // TODO: 使用Redis原子操作扣减配额
        // 暂时只更新数据库已用配额
        ApiKey entity = apiKeyMapper.selectById(keyId);
        if (entity != null) {
            entity.setUsedQuota(entity.getUsedQuota() + tokens);
            apiKeyMapper.updateById(entity);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证Key归属权
     */
    private ApiKey getAndValidateOwnership(Long userId, Long keyId) {
        ApiKey entity = apiKeyMapper.selectById(keyId);
        if (entity == null) {
            throw new BizException(ErrorCode.API_KEY_NOT_FOUND);
        }
        if (!entity.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.PERMISSION_DENIED);
        }
        return entity;
    }

    /**
     * 实体转响应
     */
    private ApiKeyResponse toResponse(ApiKey entity) {
        ApiKeyResponse resp = new ApiKeyResponse();
        BeanUtils.copyProperties(entity, resp);
        if (entity.getCreatedAt() != null) {
            resp.setCreatedAt(FORMATTER.format(entity.getCreatedAt()));
        }
        if (entity.getExpiresAt() != null) {
            resp.setExpiresAt(FORMATTER.format(entity.getExpiresAt()));
        }
        // 状态描述
        resp.setStatusDesc(entity.getStatus() == 1 ? "启用" :
                           entity.getStatus() == 0 ? "禁用" : "已轮换");
        return resp;
    }
}
