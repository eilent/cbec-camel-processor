package com.enterprise.msg.manager;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.apache.camel.Exchange;

import javax.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.enterprise.msg.constant.RabbitMQConstants;

@Component
public class EnterpriseMsgManager {
    //@Resource
    private RedisTemplate<String, String> redisTemplate;

    // 幂等校验：基于消息唯一ID
    public void checkIdempotent(Exchange exchange) {
        // 提取消息唯一ID（生产者必须传入，无则生成临时ID）
        String msgId = (String) exchange.getIn().getHeader("msgId");
        if (msgId == null) {
            msgId = UUID.randomUUID().toString();
            exchange.getIn().setHeader("msgId", msgId);
        }

        // Redis原子操作：SETNX（存在则标记为已处理）
        String redisKey = "processed_msg:" + msgId;
        Boolean isNew = redisTemplate.opsForValue().setIfAbsent(redisKey, "1", 24, TimeUnit.HOURS);
        exchange.getIn().setHeader("IS_PROCESSED", isNew == null || !isNew);
    }

    // 提取MessageType
    public void extractMessageType(Exchange exchange) {
        String messageType = (String) exchange.getIn().getHeader("MessageType");
        exchange.getIn().setHeader("MessageType", messageType);
    }

    // 手动ACK确认
    public void manualAck(Exchange exchange) {
        exchange.getIn().setHeader(RabbitMQConstants.AUTO_ACK, true);
    }
}