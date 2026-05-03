package com.bank.ai.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 银行AI网关启动类
 *
 * <p>职责定位：
 * <ul>
 *   <li>AI服务调用的统一代理与路由</li>
 *   <li>多渠道适配与故障转移</li>
 *   <li>认证授权与配额管理</li>
 *   <li>银行合规必要能力（敏感词过滤、审计日志）</li>
 * </ul>
 *
 * @author Bank AI Gateway Team
 * @since 1.0.0
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.bank.ai.gateway.repository")
public class BankGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankGatewayApplication.class, args);
    }
}