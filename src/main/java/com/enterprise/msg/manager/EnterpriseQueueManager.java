package com.enterprise.msg.manager;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EnterpriseQueueManager {
    @Value("${rabbitmq.host}")
    private String rabbitHost;
    @Value("${rabbitmq.port}")
    private int rabbitPort;
    @Value("${rabbitmq.username}")
    private String rabbitUsername;
    @Value("${rabbitmq.password}")
    private String rabbitPassword;
    
    @Value("${rabbitmq.virtual-host:}")
    private String rabbitVirtualHost;

    @Value("${enterprise.msg.rabbitmq.exchange}")
    private String exchangeName;
    @Value("${enterprise.msg.rabbitmq.exchange-type}")
    private String exchangeType;

    /**
     * 企业接入时创建专属队列并绑定到交换器
     * @param enterpriseId 企业唯一标识
     */
    public void createAndBindQueue(String enterpriseId) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(rabbitHost);
        factory.setPort(rabbitPort);
        factory.setUsername(rabbitUsername);
        factory.setPassword(rabbitPassword);
        if (!rabbitVirtualHost.isEmpty()) {
            factory.setVirtualHost(rabbitVirtualHost);
        }

        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {
            // 声明交换器（幂等，已存在则不创建）
            channel.exchangeDeclare(exchangeName, exchangeType, true, false, null);

            // 创建企业专属队列
            String queueName = "queue.enterprise." + enterpriseId;
            channel.queueDeclare(queueName, true, false, false, null);

            // 绑定队列到交换器（绑定键：enterprise.企业ID.#）
            String bindingKey = "enterprise." + enterpriseId + ".#";
            channel.queueBind(queueName, exchangeName, bindingKey);
        }
    }
}