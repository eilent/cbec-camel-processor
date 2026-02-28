package com.enterprise.msg.util;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;

/**
 * RabbitMQ消息发送工具类
 * 用于将消息发送到指定的交换器和路由键
 */
@Component
public class RabbitMqSenderUtil {

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

    private ConnectionFactory connectionFactory;

    @PostConstruct
    public void init() {
        // 初始化连接工厂
        connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(rabbitHost);
        connectionFactory.setPort(rabbitPort);
        connectionFactory.setUsername(rabbitUsername);
        connectionFactory.setPassword(rabbitPassword);
        if (!rabbitVirtualHost.isEmpty()) {
            connectionFactory.setVirtualHost(rabbitVirtualHost);
        }
    }

    /**
     * 发送消息到指定的交换器和路由键
     * @param exchangeName 交换器名称
     * @param routingKey 路由键
     * @param message 消息内容
     * @throws IOException IO异常
     * @throws TimeoutException 超时异常
     */
    public void sendMessage(String exchangeName, String routingKey, String message) throws IOException, TimeoutException {
        sendMessage(exchangeName, routingKey, message, null);
    }

    /**
     * 发送消息到指定的交换器和路由键，支持消息头
     * @param exchangeName 交换器名称
     * @param routingKey 路由键
     * @param message 消息内容
     * @param headers 消息头
     * @throws IOException IO异常
     * @throws TimeoutException 超时异常
     */
    public void sendMessage(String exchangeName, String routingKey, String message, Map<String, String> headers) throws IOException, TimeoutException {
        Connection connection = null;
        Channel channel = null;

        try {
            // 创建连接
            connection = connectionFactory.newConnection();
            // 创建通道
            channel = connection.createChannel();
            
            // 构建消息属性
            AMQP.BasicProperties.Builder propertiesBuilder = new AMQP.BasicProperties.Builder();
            if (headers != null && !headers.isEmpty()) {
                // 转换Map<String, String>为Map<String, Object>
                java.util.Map<String, Object> objectHeaders = new java.util.HashMap<>();
                for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                    objectHeaders.put(entry.getKey(), entry.getValue());
                }
                propertiesBuilder.headers(objectHeaders);
            }
            AMQP.BasicProperties properties = propertiesBuilder.build();
            
            // 发送消息
            channel.basicPublish(exchangeName, routingKey, properties, message.getBytes());
        } finally {
            // 关闭通道和连接
            if (channel != null) {
                channel.close();
            }
            if (connection != null) {
                connection.close();
            }
        }
    }
}
