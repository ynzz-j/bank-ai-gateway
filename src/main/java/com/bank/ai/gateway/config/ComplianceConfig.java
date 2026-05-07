package com.bank.ai.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 合规过滤配置
 * 对应 application.yml 中的 compliance 配置�? */
@Data
@Configuration
@ConfigurationProperties(prefix = "compliance")
public class ComplianceConfig {

    /** 关键词过滤配�?*/
    private KeywordConfig keyword = new KeywordConfig();

    /** 免责声明配置 */
    private DisclaimerConfig disclaimer = new DisclaimerConfig();

    /** 脱敏配置 */
    private MaskConfig mask = new MaskConfig();

    @Data
    public static class KeywordConfig {
        /** 词库来源：DATABASE / FILE */
        private String source = "DATABASE";

        /** 重载间隔（秒�?*/
        private int reloadInterval = 300;

        /** 是否启用组合规则 */
        private boolean comboRuleEnabled = true;

        /** 文件路径（FILE模式时使用） */
        private String filePath;
    }

    @Data
    public static class DisclaimerConfig {
        /** 是否启用 */
        private boolean enabled = true;

        /** 声明模板 */
        private String template = "【风险提示】本回复由人工智能生成，仅供参考，不构成任何投资建议或专业意见。\n" +
                "银行不对 AI 生成内容的准确性、完整性和可靠性承担责任。如需专业服务，请咨询相关专业人士";
    }

    @Data
    public static class MaskConfig {
        /** 是否脱敏手机�?*/
        private boolean phone = true;

        /** 是否脱敏身份�?*/
        private boolean idCard = true;

        /** 是否脱敏银行�?*/
        private boolean bankCard = true;

        /** 是否脱敏邮箱 */
        private boolean email = true;
    }
}
