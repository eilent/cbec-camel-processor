package com.enterprise.msg.api;

import lombok.Data;
import java.util.List;

/**
 * 企业消息请求体
 * 用于接收企业发送的订单、运单或支付单数据
 */
@Data
public class EnterpriseMessageRequest {
    /**
     * 企业编号ID
     */
    private String enterpriseId;

    /**
     * 报文类型（如：CBEC001、CBEC002等）
     */
    private String messageType;

    /**
     * 报文内容集合（报文内容为xml字符串）
     */
    private List<String> messageContents;

    /**
     * rabbitmq交换器名称
     */
    private String exchangeName;

    /**
     * 路由键
     */
    private String routingKey;
}
