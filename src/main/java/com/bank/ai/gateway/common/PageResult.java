package com.bank.ai.gateway.common;

import lombok.Data;

import java.util.List;

/**
 * 分页结果
 *
 * @param <T> 数据类型
 * @since 1.0.0
 */
@Data
public class PageResult<T> {

    /** 数据列表 */
    private List<T> list;

    /** 总数 */
    private long total;

    /** 当前页 */
    private int page;

    /** 每页大小 */
    private int size;

    public PageResult() {
    }

    public PageResult(List<T> list, long total, int page, int size) {
        this.list = list;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        return new PageResult<>(list, total, page, size);
    }
}