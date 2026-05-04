package com.bank.ai.gateway.controller.apikey;

import com.bank.ai.gateway.common.ApiResponse;
import com.bank.ai.gateway.model.dto.request.apikey.CreateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.RotateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.UpdateApiKeyRequest;
import com.bank.ai.gateway.model.dto.response.apikey.ApiKeyResponse;
import com.bank.ai.gateway.model.dto.response.apikey.CreateApiKeyResponse;
import com.bank.ai.gateway.service.apikey.ApiKeyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API Key 管理 Controller
 *
 * <p>权限：需要 ADMIN 角色或所属用户本人
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    /**
     * 创建 API Key
     *
     * <p>⚠️ 完整Key仅在响应中返回一次，请妥善保存
     */
    @PostMapping
    public ApiResponse<CreateApiKeyResponse> create(@RequestBody CreateApiKeyRequest request) {
        Long userId = getCurrentUserId();
        CreateApiKeyResponse response = apiKeyService.create(userId, request);
        return ApiResponse.success(response, "API Key 创建成功，请立即保存完整Key！");
    }

    /**
     * 列出当前用户的所有 API Key
     */
    @GetMapping
    public ApiResponse<List<ApiKeyResponse>> list() {
        Long userId = getCurrentUserId();
        List<ApiKeyResponse> list = apiKeyService.listByUser(userId);
        return ApiResponse.success(list);
    }

    /**
     * 更新 API Key
     */
    @PutMapping("/{id}")
    public ApiResponse<ApiKeyResponse> update(
            @PathVariable Long id,
            @RequestBody UpdateApiKeyRequest request) {
        Long userId = getCurrentUserId();
        ApiKeyResponse response = apiKeyService.update(userId, id, request);
        return ApiResponse.success(response, "更新成功");
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        apiKeyService.delete(userId, id);
        return ApiResponse.success(null, "删除成功");
    }

    /**
     * 禁用 API Key
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Void> disable(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        apiKeyService.disable(userId, id);
        return ApiResponse.success(null, "已禁用");
    }

    /**
     * 启用 API Key
     */
    @PostMapping("/{id}/enable")
    public ApiResponse<Void> enable(@PathVariable Long id) {
        Long userId = getCurrentUserId();
        apiKeyService.enable(userId, id);
        return ApiResponse.success(null, "已启用");
    }

    /**
     * 轮换 API Key
     *
     * <p>旧Key标记为已轮换，生成新Key
     * <p>⚠️ 新Key仅在响应中返回一次
     */
    @PostMapping("/{id}/rotate")
    public ApiResponse<CreateApiKeyResponse> rotate(
            @PathVariable Long id,
            @RequestBody(required = false) RotateApiKeyRequest request) {
        Long userId = getCurrentUserId();
        if (request == null) request = new RotateApiKeyRequest();
        CreateApiKeyResponse response = apiKeyService.rotate(userId, id, request);
        return ApiResponse.success(response, "API Key 已轮换，请立即保存新Key！");
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前用户ID
     *
     * <p>TODO: 从 JWT/SecurityContext 中获取
     * 暂时返回固定值（测试用）
     */
    private Long getCurrentUserId() {
        // TODO: 从 Spring Security 或 JWT 中解析
        // 示例代码：
        // Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        // return ((UserDetails) auth.getPrincipal()).getUserId();
        return 1L; // 临时：返回ID=1的用户
    }
}