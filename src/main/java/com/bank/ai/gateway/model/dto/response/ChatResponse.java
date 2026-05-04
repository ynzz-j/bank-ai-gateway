package com.bank.ai.gateway.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 聊天补全响应
 *
 * <p>OpenAI 标准格式。
 *
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * 响应 ID
     */
    private String id;

    /**
     * 对象类型
     */
    private String object;

    /**
     * 创建时间（Unix 时间戳）
     */
    private Long created;

    /**
     * 模型名称
     */
    private String model;

    /**
     * 选择列表
     */
    private List<Choice> choices;

    /**
     * Token 用量
     */
    private Usage usage;

    /**
     * 选择项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Choice {
        /**
         * 索引
         */
        private Integer index;

        /**
         * 消息
         */
        private Message message;

        /**
         * 结束原因：stop/length/content_filter
         */
        @JsonProperty("finish_reason")
        private String finishReason;
    }

    /**
     * 消息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Message {
        /**
         * 角色
         */
        private String role;

        /**
         * 内容
         */
        private String content;
    }

    /**
     * Token 用量
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Usage {
        /**
         * 输入 token 数
         */
        @JsonProperty("prompt_tokens")
        private Integer promptTokens;

        /**
         * 输出 token 数
         */
        @JsonProperty("completion_tokens")
        private Integer completionTokens;

        /**
         * 总 token 数
         */
        @JsonProperty("total_tokens")
        private Integer totalTokens;
    }
}
