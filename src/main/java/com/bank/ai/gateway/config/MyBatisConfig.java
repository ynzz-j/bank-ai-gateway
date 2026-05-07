package com.bank.ai.gateway.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus配置
 *
 * <p>配置MyBatis-Plus拦截器：
 * <ul>
 *   <li>分页插件（PostgreSQL）</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Configuration
@MapperScan("com.bank.ai.gateway.repository")
public class MyBatisConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 分页插件
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        paginationInterceptor.setMaxLimit(100L);  // 最大分页限制
        interceptor.addInnerInterceptor(paginationInterceptor);
        return interceptor;
    }
}