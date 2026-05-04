package com.bank.ai.gateway.model.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 聊天补全请求
 *
 * <p>OpenAI 兼容格式。
 *
 * @since 1.0.0
 */
@Data
public class ChatRequest {

    /**
     * 模型名称（统一模型名）
     */
    @NotNull(message = "model 不能为空")
    private String model;

    /**
     * 消息列表
     */
    @NotEmpty(message = "messages 不能为空")
    private List<Message> messages;

    /**
     * 是否流式输出
     */
    private Boolean stream = false;

    /**
     * 温度参数（0-2）
     */
    private Double temperature;

    /**
     * 最大输出 token 数
     */
    @JsonProperty("max_tokens")
    private Integer maxTokens;

    /**
     * Top-p 采样
     */
    @JsonProperty("top_p")
    private Double topP;

    /**
     * 停止词
     */
    private List<String> stop;

    /**
     * 频率惩罚（-2.0 到 2.0）
     */
    @JsonProperty("frequency_penalty")
    private Double frequencyPenalty;

    /**
     * 存在惩罚（-2.0 到 2.0）
     */
    @JsonProperty("presence_penalty")
    private Double presencePenalty;

    /**
     * 用户标识
     */
    private String user;

    /**
     * 消息
     */
    @Data
    public static class Message {
        /**
         * 角色：system/user/assistant
         */
        private String role;

        /**
         * 内容
         */
        private String content;

        /**
         * 名称（可选）
         */
        private String name;
    }
}
