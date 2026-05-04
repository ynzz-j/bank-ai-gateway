package com.bank.ai.gateway.service.apikey;

import com.bank.ai.gateway.model.dto.request.apikey.CreateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.RotateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.UpdateApiKeyRequest;
import com.bank.ai.gateway.model.dto.response.apikey.ApiKeyResponse;
import com.bank.ai.gateway.model.dto.response.apikey.CreateApiKeyResponse;
import com.bank.ai.gateway.model.entity.apikey.ApiKeyEntity;

import java.util.List;

/**
 * API Key Service 接口
 *
 * @since 1.0.0
 */
public interface ApiKeyService {

    CreateApiKeyResponse create(Long userId, CreateApiKeyRequest request);
    List<ApiKeyResponse> listByUser(Long userId);
    ApiKeyResponse update(Long userId, Long keyId, UpdateApiKeyRequest request);
    void delete(Long userId, Long keyId);
    void disable(Long userId, Long keyId);
    void enable(Long userId, Long keyId);
    CreateApiKeyResponse rotate(Long userId, Long keyId, RotateApiKeyRequest request);
    ApiKeyEntity validate(String apiKey);
    boolean checkQuota(Long keyId, int tokens);
    void consumeQuota(Long keyId, int tokens);
}