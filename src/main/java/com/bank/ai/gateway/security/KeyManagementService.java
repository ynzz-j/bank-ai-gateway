package com.bank.ai.gateway.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 密钥管理服务（简化实现）
 *
 * <p>MVP 阶段使用配置文件密钥，生产环境应接入银行 KMS。
 *
 * @since 1.0.0
 */
@Service
public class KeyManagementService implements IKeyManagementService {

    @Value("${security.kms.key:changeme-default-32bytes!}")
    private String masterKey;

    // 密钥缓存
    private final Map<String, SecretKey> keyCache = new ConcurrentHashMap<>();

    @Override
    public SecretKey getKey(String version) {
        return keyCache.computeIfAbsent(version, v -> {
            try {
                // 从主密钥派生版本密钥
                String derivedKey = deriveKey(masterKey, v);
                byte[] keyBytes = derivedKey.getBytes(StandardCharsets.UTF_8);
                
                // 确保是 256 位（32字节）
                byte[] keyBytes32 = new byte[32];
                System.arraycopy(keyBytes, 0, keyBytes32, 0, Math.min(32, keyBytes.length));
                
                return new SecretKeySpec(keyBytes32, "AES");
            } catch (Exception e) {
                throw new RuntimeException("密钥派生失败: " + e.getMessage(), e);
            }
        });
    }

    @Override
    public String getCurrentVersion() {
        return "v1";
    }

    @Override
    public List<String> getVersions() {
        return List.of("v1");
    }

    /**
     * 从主密钥派生版本密钥
     */
    private String deriveKey(String masterKey, String version) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest((masterKey + ":" + version).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }
}