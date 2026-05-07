package com.bank.ai.gateway.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 渠道 API Key 加密器
 *
 * <p>使用 AES-256-GCM 加密，保护渠道的 API Key。
 * MVP 阶段使用配置文件密钥，生产环境应接入 KMS。
 *
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChannelKeyEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    private final IKeyManagementService keyManagementService;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 加密 API Key
     *
     * @param plainKey 明文 API Key
     * @return 加密结果（包含密文、IV、KMS版本）
     */
    public EncryptedKey encrypt(String plainKey) {
        try {
            // 获取当前 KMS 密钥版本
            String kmsKeyVersion = keyManagementService.getCurrentVersion();
            SecretKey key = keyManagementService.getKey(kmsKeyVersion);

            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            // 加密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, parameterSpec);

            byte[] cipherText = cipher.doFinal(plainKey.getBytes(StandardCharsets.UTF_8));

            // 编码结果
            return new EncryptedKey(
                    Base64.getEncoder().encodeToString(cipherText),
                    Base64.getEncoder().encodeToString(iv),
                    kmsKeyVersion
            );

        } catch (Exception e) {
            log.error("Failed to encrypt API key: {}", e.getMessage());
            throw new RuntimeException("API Key 加密失败", e);
        }
    }

    /**
     * 解密 API Key
     *
     * @param encrypted 加密结果
     * @return 明文 API Key
     */
    public String decrypt(EncryptedKey encrypted) {
        try {
            // 使用指定版本的 KMS 密钥解密
            SecretKey key = keyManagementService.getKey(encrypted.kmsKeyVersion());

            // 解码
            byte[] cipherText = Base64.getDecoder().decode(encrypted.cipherText());
            byte[] iv = Base64.getDecoder().decode(encrypted.iv());

            // 解密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, parameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);

        } catch (Exception e) {
            log.error("Failed to decrypt API key: {}", e.getMessage());
            throw new RuntimeException("API Key 解密失败", e);
        }
    }

    /**
     * 加密结果记录
     *
     * @param cipherText 密文（Base64编码）
     * @param iv IV（Base64编码）
     * @param kmsKeyVersion KMS密钥版本
     */
    public record EncryptedKey(String cipherText, String iv, String kmsKeyVersion) {}
}