package com.bank.ai.gateway.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bank.ai.gateway.model.entity.User;
import org.springframework.stereotype.Repository;

/**
 * 用户 Mapper
 *
 * @since 1.0.0
 */
@Repository
public interface UserMapper extends BaseMapper<User> {
}