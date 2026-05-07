package com.bank.ai.gateway.service.channel;

import com.bank.ai.gateway.common.BizException;
import com.bank.ai.gateway.common.ErrorCode;
import com.bank.ai.gateway.model.dto.request.channel.ChannelCreateRequest;
import com.bank.ai.gateway.model.dto.request.channel.ChannelUpdateRequest;
import com.bank.ai.gateway.model.entity.channel.Channel;
import com.bank.ai.gateway.repository.ChannelMapper;
import com.bank.ai.gateway.security.ChannelKeyEncryptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 渠道服务
 *
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelMapper channelMapper;

    /**
     * 创建渠道
     */
    @Transactional
    public Channel createChannel(ChannelCreateRequest request, ChannelKeyEncryptor.EncryptedKey encrypted) {
        Channel channel = new Channel();
        channel.setName(request.getName());
        channel.setProvider(request.getProvider());
        channel.setBaseUrl(request.getBaseUrl());
        channel.setApiKeyEncrypted(encrypted.cipherText());
        channel.setApiKeyNonce(encrypted.iv());
        channel.setApiKeyKmsVersion(encrypted.kmsKeyVersion());
        channel.setModels(request.getModels());
        channel.setPriority(request.getPriority());
        channel.setWeight(request.getWeight());
        channel.setStatus((short) Channel.Status.ENABLED.getCode());
        channel.setCreatedAt(LocalDateTime.now());
        channel.setUpdatedAt(LocalDateTime.now());

        channelMapper.insert(channel);
        log.info("Channel created: id={}, name={}, provider={}", channel.getId(), channel.getName(), channel.getProvider());

        return channel;
    }

    /**
     * 更新渠道
     */
    @Transactional
    public Channel updateChannel(Long id, ChannelUpdateRequest request, ChannelKeyEncryptor.EncryptedKey encrypted) {
        Channel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }

        if (request.getName() != null) {
            channel.setName(request.getName());
        }
        if (encrypted != null) {
            channel.setApiKeyEncrypted(encrypted.cipherText());
            channel.setApiKeyNonce(encrypted.iv());
            channel.setApiKeyKmsVersion(encrypted.kmsKeyVersion());
        }
        if (request.getBaseUrl() != null) {
            channel.setBaseUrl(request.getBaseUrl());
        }
        if (request.getModels() != null) {
            channel.setModels(request.getModels());
        }
        if (request.getPriority() != null) {
            channel.setPriority(request.getPriority());
        }
        if (request.getWeight() != null) {
            channel.setWeight(request.getWeight());
        }
        if (request.getStatus() != null) {
            channel.setStatus(request.getStatus());
        }
        channel.setUpdatedAt(LocalDateTime.now());

        channelMapper.update(channel);
        log.info("Channel updated: id={}", id);

        return channel;
    }

    /**
     * 删除渠道
     */
    @Transactional
    public void deleteChannel(Long id) {
        Channel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }

        channelMapper.delete(id);
        log.info("Channel deleted: id={}", id);
    }

    /**
     * 启用渠道
     */
    @Transactional
    public Channel enableChannel(Long id) {
        Channel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }

        channel.setStatus((short) Channel.Status.ENABLED.getCode());
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.update(channel);
        log.info("Channel enabled: id={}", id);

        return channel;
    }

    /**
     * 禁用渠道
     */
    @Transactional
    public Channel disableChannel(Long id) {
        Channel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }

        channel.setStatus((short) Channel.Status.UNAVAILABLE.getCode());
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.update(channel);
        log.info("Channel disabled: id={}", id);

        return channel;
    }

    /**
     * 更新加密密钥
     */
    @Transactional
    public void updateEncryptedKey(Long id, ChannelKeyEncryptor.EncryptedKey encrypted) {
        Channel channel = channelMapper.findById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }
        channel.setApiKeyEncrypted(encrypted.cipherText());
        channel.setApiKeyNonce(encrypted.iv());
        channel.setApiKeyKmsVersion(encrypted.kmsKeyVersion());
        channel.setUpdatedAt(LocalDateTime.now());
        channelMapper.update(channel);
    }

    /**
     * 获取渠道
     */
    public Channel getChannelById(Long id) {
        return channelMapper.findById(id);
    }

    /**
     * 列表查询
     */
    public List<Channel> listChannels(int page, int size, String provider, Boolean enabled) {
        int offset = (page - 1) * size;
        Short status = null;
        if (enabled != null) {
            status = enabled ? (short) Channel.Status.ENABLED.getCode() : (short) Channel.Status.UNAVAILABLE.getCode();
        }
        return channelMapper.selectPage(offset, size, provider, status);
    }

    /**
     * 统计数量
     */
    public int countChannels(String provider, Boolean enabled) {
        Short status = null;
        if (enabled != null) {
            status = enabled ? (short) Channel.Status.ENABLED.getCode() : (short) Channel.Status.UNAVAILABLE.getCode();
        }
        Integer count = channelMapper.count(provider, status);
        return count != null ? count : 0;
    }

    /**
     * 获取所有启用的渠道
     */
    public List<Channel> getEnabledChannels() {
        return channelMapper.selectEnabled();
    }

    /**
     * 获取指定提供商的渠道
     */
    public List<Channel> getChannelsByProvider(String provider) {
        return channelMapper.selectByProvider(provider);
    }
}