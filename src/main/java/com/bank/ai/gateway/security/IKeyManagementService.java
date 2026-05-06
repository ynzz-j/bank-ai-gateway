package com.bank.ai.gateway.security;

import javax.crypto.SecretKey;
import java.util.List;

/**
 * 密钥管理服务接口
 *
 * <p>定义 KMS 服务接口，支持多版本密钥管理。
 * 生产环境应对接银行内部 KMS 或 HashiCorp Vault。
 *
 * @since 1.0.0
 */
public interface IKeyManagementService {

    /**
     * 获取指定版本的加密密钥
     *
     * @param version 密钥版本号
     * @return 加密密钥
     */
    SecretKey getKey(String version);

    /**
     * 获取当前密钥版本号
     *
     * @return 当前版本
     */
    String getCurrentVersion();

    /**
     * 获取所有密钥版本
     *
     * @return 版本列表
     */
    List<String> getVersions();
}