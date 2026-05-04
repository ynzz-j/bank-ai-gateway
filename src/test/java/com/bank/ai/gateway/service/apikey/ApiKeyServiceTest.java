package com.bank.ai.gateway.service.apikey;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.apikey.CreateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.RotateApiKeyRequest;
import com.bank.ai.gateway.model.dto.request.apikey.UpdateApiKeyRequest;
import com.bank.ai.gateway.model.dto.response.apikey.ApiKeyResponse;
import com.bank.ai.gateway.model.dto.response.apikey.CreateApiKeyResponse;
import com.bank.ai.gateway.model.entity.apikey.ApiKeyEntity;
import com.bank.ai.gateway.repository.apikey.ApiKeyMapper;
import com.bank.ai.gateway.service.apikey.impl.ApiKeyServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * ApiKeyService 单元测试
 *
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ApiKeyService")
class ApiKeyServiceTest {

    @Mock
    private ApiKeyMapper apiKeyMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @InjectMocks
    private ApiKeyServiceImpl apiKeyService;

    private Long userId;
    private ApiKeyEntity testEntity;

    @BeforeEach
    void setUp() {
        userId = 1L;

        testEntity = new ApiKeyEntity();
        testEntity.setId(100L);
        testEntity.setUserId(userId);
        testEntity.setKeyPrefix("bgk_");
        testEntity.setKeyHash("testHash");
        testEntity.setName("Test Key");
        testEntity.setQuotaRpm(60);
        testEntity.setQuotaTpm(100000);
        testEntity.setQuotaTotal(-1L);
        testEntity.setUsedQuota(0L);
        testEntity.setStatus(1);
        testEntity.setCreatedAt(LocalDateTime.now());
    }

    @Nested
    @DisplayName("create")
    class CreateTest {

        @Test
        @DisplayName("创建成功 - 默认配额")
        void create_withDefaultQuota_success() {
            // Arrange
            CreateApiKeyRequest request = new CreateApiKeyRequest();
            request.setName("My API Key");

            when(apiKeyMapper.insert(any(ApiKeyEntity.class))).thenAnswer(invocation -> {
                ApiKeyEntity e = invocation.getArgument(0);
                e.setId(100L);
                e.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            // Act
            CreateApiKeyResponse response = apiKeyService.create(userId, request);

            // Assert
            assertNotNull(response);
            assertNotNull(response.getApiKey());
            assertTrue(response.getApiKey().startsWith("bgk_"));
            assertEquals(28, response.getApiKey().length());
            assertEquals("My API Key", response.getName());
            assertEquals(60, response.getQuotaRpm());
            assertEquals(100000, response.getQuotaTpm());
            verify(apiKeyMapper).insert(any(ApiKeyEntity.class));
        }

        @Test
        @DisplayName("创建成功 - 自定义配额")
        void create_withCustomQuota_success() {
            // Arrange
            CreateApiKeyRequest request = new CreateApiKeyRequest();
            request.setName("Custom Key");
            request.setQuotaRpm(120);
            request.setQuotaTpm(200000);
            request.setQuotaTotal(1000000L);
            request.setExpiresAt("2026-12-31 23:59:59");

            when(apiKeyMapper.insert(any(ApiKeyEntity.class))).thenAnswer(invocation -> {
                ApiKeyEntity e = invocation.getArgument(0);
                e.setId(101L);
                e.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            // Act
            CreateApiKeyResponse response = apiKeyService.create(userId, request);

            // Assert
            assertEquals(120, response.getQuotaRpm());
            assertEquals(200000, response.getQuotaTpm());
            assertEquals(1000000L, response.getQuotaTotal());
            assertNotNull(response.getExpiresAt());
        }
    }

    @Nested
    @DisplayName("listByUser")
    class ListByUserTest {

        @Test
        @DisplayName("查询成功 - 有数据")
        void listByUser_withData_success() {
            // Arrange
            when(apiKeyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(testEntity));

            // Act
            List<ApiKeyResponse> result = apiKeyService.listByUser(userId);

            // Assert
            assertEquals(1, result.size());
            assertEquals("Test Key", result.get(0).getName());
            assertEquals("启用", result.get(0).getStatusDesc());
        }

        @Test
        @DisplayName("查询成功 - 空数据")
        void listByUser_emptyData_success() {
            // Arrange
            when(apiKeyMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            // Act
            List<ApiKeyResponse> result = apiKeyService.listByUser(userId);

            // Assert
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("validate")
    class ValidateTest {

        @Test
        @DisplayName("验证成功")
        void validate_success() {
            // Arrange
            String apiKey = "bgk_abcdefghijklmnopqrstuvwx";
            when(apiKeyMapper.selectByPrefixAndHash(eq("bgk_"), any())).thenReturn(testEntity);

            // Act
            ApiKeyEntity result = apiKeyService.validate(apiKey);

            // Assert
            assertNotNull(result);
            assertEquals(testEntity.getId(), result.getId());
        }

        @Test
        @DisplayName("验证失败 - Key为空")
        void validate_nullKey_returnsNull() {
            // Act
            ApiKeyEntity result = apiKeyService.validate(null);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("验证失败 - Key过短")
        void validate_shortKey_returnsNull() {
            // Act
            ApiKeyEntity result = apiKeyService.validate("bgk");

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("验证失败 - Key不存在")
        void validate_notFound_returnsNull() {
            // Arrange
            String apiKey = "bgk_abcdefghijklmnopqrstuvwx";
            when(apiKeyMapper.selectByPrefixAndHash(eq("bgk_"), any())).thenReturn(null);

            // Act
            ApiKeyEntity result = apiKeyService.validate(apiKey);

            // Assert
            assertNull(result);
        }

        @Test
        @DisplayName("验证失败 - Key已禁用")
        void validate_disabledKey_throwsException() {
            // Arrange
            testEntity.setStatus(0);
            String apiKey = "bgk_abcdefghijklmnopqrstuvwx";
            when(apiKeyMapper.selectByPrefixAndHash(eq("bgk_"), any())).thenReturn(testEntity);

            // Act & Assert
            BizException ex = assertThrows(BizException.class, () -> apiKeyService.validate(apiKey));
            assertEquals(ErrorCode.API_KEY_DISABLED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("验证失败 - Key已轮换")
        void validate_rotatedKey_throwsException() {
            // Arrange
            testEntity.setStatus(2);
            String apiKey = "bgk_abcdefghijklmnopqrstuvwx";
            when(apiKeyMapper.selectByPrefixAndHash(eq("bgk_"), any())).thenReturn(testEntity);

            // Act & Assert
            BizException ex = assertThrows(BizException.class, () -> apiKeyService.validate(apiKey));
            assertEquals(ErrorCode.API_KEY_REVOKED.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("验证失败 - Key已过期")
        void validate_expiredKey_throwsException() {
            // Arrange
            testEntity.setExpiresAt(LocalDateTime.now().minusDays(1));
            String apiKey = "bgk_abcdefghijklmnopqrstuvwx";
            when(apiKeyMapper.selectByPrefixAndHash(eq("bgk_"), any())).thenReturn(testEntity);

            // Act & Assert
            BizException ex = assertThrows(BizException.class, () -> apiKeyService.validate(apiKey));
            assertEquals(ErrorCode.API_KEY_EXPIRED.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("disable / enable")
    class DisableEnableTest {

        @Test
        @DisplayName("禁用成功")
        void disable_success() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

            // Act
            apiKeyService.disable(userId, 100L);

            // Assert
            verify(apiKeyMapper).updateById(any(ApiKeyEntity.class));
        }

        @Test
        @DisplayName("启用成功")
        void enable_success() {
            // Arrange
            testEntity.setStatus(0);
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

            // Act
            apiKeyService.enable(userId, 100L);

            // Assert
            verify(apiKeyMapper).updateById(any(ApiKeyEntity.class));
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTest {

        @Test
        @DisplayName("删除成功")
        void delete_success() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.deleteById(100L)).thenReturn(1);

            // Act
            apiKeyService.delete(userId, 100L);

            // Assert
            verify(apiKeyMapper).deleteById(100L);
        }

        @Test
        @DisplayName("删除失败 - 无权限")
        void delete_noPermission_throwsException() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);

            // Act & Assert
            BizException ex = assertThrows(BizException.class,
                    () -> apiKeyService.delete(999L, 100L));
            assertEquals(ErrorCode.PERMISSION_DENIED.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTest {

        @Test
        @DisplayName("更新成功")
        void update_success() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

            UpdateApiKeyRequest request = new UpdateApiKeyRequest();
            request.setName("Updated Name");
            request.setQuotaRpm(200);

            // Act
            ApiKeyResponse response = apiKeyService.update(userId, 100L, request);

            // Assert
            assertEquals("Updated Name", response.getName());
            verify(apiKeyMapper).updateById(any(ApiKeyEntity.class));
        }
    }

    @Nested
    @DisplayName("rotate")
    class RotateTest {

        @Test
        @DisplayName("轮换成功")
        void rotate_success() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);
            when(apiKeyMapper.insert(any(ApiKeyEntity.class))).thenAnswer(invocation -> {
                ApiKeyEntity e = invocation.getArgument(0);
                e.setId(101L);
                e.setCreatedAt(LocalDateTime.now());
                return 1;
            });

            RotateApiKeyRequest request = new RotateApiKeyRequest();

            // Act
            CreateApiKeyResponse response = apiKeyService.rotate(userId, 100L, request);

            // Assert
            assertNotNull(response);
            assertTrue(response.getApiKey().startsWith("bgk_"));
            verify(apiKeyMapper).updateById(any(ApiKeyEntity.class)); // 标记旧Key
            verify(apiKeyMapper).insert(any(ApiKeyEntity.class)); // 创建新Key
        }
    }

    @Nested
    @DisplayName("quota")
    class QuotaTest {

        @Test
        @DisplayName("检查配额 - 允许")
        void checkQuota_allowed() {
            // Act
            boolean result = apiKeyService.checkQuota(100L, 1000);

            // Assert
            assertTrue(result);
        }

        @Test
        @DisplayName("消费配额")
        void consumeQuota_success() {
            // Arrange
            when(apiKeyMapper.selectById(100L)).thenReturn(testEntity);
            when(apiKeyMapper.updateById(any(ApiKeyEntity.class))).thenReturn(1);

            // Act
            apiKeyService.consumeQuota(100L, 500);

            // Assert
            verify(apiKeyMapper).updateById(any(ApiKeyEntity.class));
        }
    }
}
