package com.bank.ai.gateway.model.dto.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 日期范围请求基类
 *
 * <p>统一日期时间格式：yyyy-MM-dd HH:mm:ss
 *
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class DateRangeRequest extends PageRequest {

    /**
     * 开始时间
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime startTime;

    /**
     * 结束时间
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    protected LocalDateTime endTime;

    /**
     * 获取开始时间（默认7天前）
     */
    public LocalDateTime getStartDateTime() {
        return startTime != null ? startTime : LocalDateTime.now().minusDays(7);
    }

    /**
     * 获取结束时间（默认当前时间）
     */
    public LocalDateTime getEndDateTime() {
        return endTime != null ? endTime : LocalDateTime.now();
    }

    /**
     * 验证日期范围是否有效
     */
    @AssertTrue(message = "开始时间不能晚于结束时间")
    public boolean isDateRangeValid() {
        if (startTime == null || endTime == null) {
            return true;
        }
        return !startTime.isAfter(endTime);
    }
}
