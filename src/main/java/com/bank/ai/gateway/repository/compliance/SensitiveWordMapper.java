package com.bank.ai.gateway.repository.compliance;

import com.bank.ai.gateway.model.entity.SensitiveWord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 敏感词 Mapper
 */
public interface SensitiveWordMapper {

    /**
     * 查询所有启用的敏感词
     */
    List<SensitiveWord> selectAllEnabled();

    /**
     * 按分类查询敏感词
     */
    List<SensitiveWord> selectByCategory(@Param("category") String category);

    /**
     * 插入敏感词
     */
    int insert(SensitiveWord entity);

    /**
     * 批量插入敏感词
     */
    int batchInsert(@Param("list") List<SensitiveWord> list);

    /**
     * 更新敏感词状态
     */
    int updateEnabled(@Param("id") Long id, @Param("enabled") Short enabled);

    /**
     * 删除敏感词
     */
    int deleteById(@Param("id") Long id);
}
