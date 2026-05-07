package com.bank.ai.gateway.model.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求基类
 *
 * @since 1.0.0
 */
@Data
public class PageRequest {

    @Min(value = 1, message = "页码最小为1")
    protected int page = 1;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 100, message = "每页条数最大为100")
    protected int size = 20;

    public int getOffset() {
        return (page - 1) * size;
    }
}