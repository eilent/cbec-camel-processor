package com.enterprise.msg.route;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.rabbitmq.RabbitMQConstants;
import org.apache.camel.component.kafka.KafkaConstants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.enterprise.msg.manager.EnterpriseMsgManager;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.util.List;

@Component
public class EnterpriseMsgRoute extends RouteBuilder {
    // 基础配置
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
    
    @Value("${kafka.bootstrap-servers}")
    private String kafkaServers;

    // 从配置文件读取的参数
    @Value("${enterprise.msg.rabbitmq.exchange}")
    private String rabbitmqExchange;
    @Value("${enterprise.msg.kafka.topic.order}")
    private String kafkaTopicOrder;
    @Value("${enterprise.msg.kafka.topic.waybill}")
    private String kafkaTopicWaybill;
    @Value("${enterprise.msg.kafka.topic.payment}")
    private String kafkaTopicPayment;
    @Value("${enterprise.msg.rabbitmq.dlx-queue}")
    private String rabbitmqDlxQueue;
    @Value("${enterprise.msg.rabbitmq.unknown-queue}")
    private String rabbitmqUnknownQueue;
    @Value("${enterprise.msg.message-type.order}")
    private String messageTypeOrder;
    @Value("${enterprise.msg.message-type.waybill}")
    private String messageTypeWaybill;
    @Value("${enterprise.msg.message-type.payment}")
    private String messageTypePayment;
    
    // RabbitMQ消费者配置
    @Value("${enterprise.msg.rabbitmq.consumer.declare:false}")
    private boolean rabbitmqConsumerDeclare;
    @Value("${enterprise.msg.rabbitmq.consumer.durable:true}")
    private boolean rabbitmqConsumerDurable;
    @Value("${enterprise.msg.rabbitmq.consumer.auto-ack:false}")
    private boolean rabbitmqConsumerAutoAck;
    @Value("${enterprise.msg.rabbitmq.consumer.concurrent-consumers:5}")
    private int rabbitmqConsumerConcurrentConsumers;
    @Value("${enterprise.msg.rabbitmq.consumer.prefetch-count:2}")
    private int rabbitmqConsumerPrefetchCount;
    @Value("${enterprise.msg.rabbitmq.consumer.request-timeout:30000}")
    private int rabbitmqConsumerRequestTimeout;
    
    // Kafka消息key配置
    @Value("${enterprise.msg.kafka.key.header-sender-id:SenderID}")
    private String kafkaKeyHeaderSenderId;
    @Value("${enterprise.msg.kafka.key.header-message-type:MessageType}")
    private String kafkaKeyHeaderMessageType;
    @Value("${enterprise.msg.kafka.key.header-message-id:MessageID}")
    private String kafkaKeyHeaderMessageId;
    @Value("${enterprise.msg.kafka.key.separator:-}")
    private String kafkaKeySeparator;

    @Override
    public void configure() throws Exception {
        // 全局异常处理：重试3次后进入死信队列
        onException(Exception.class)
                .maximumRedeliveries(3)
                .redeliveryDelay(1000)
                .handled(true)
                .to(String.format("rabbitmq:%s?hostname=%s&portNumber=%d&username=%s&password=%s%s&exchangeType=topic&queue=%s&routingKey=enterprise.dlx.#&declare=false",
                        rabbitmqExchange, rabbitHost, rabbitPort, rabbitUsername, rabbitPassword, 
                        rabbitVirtualHost.isEmpty() ? "" : "&vhost=" + rabbitVirtualHost,
                        rabbitmqDlxQueue));

        // 动态扫描并消费所有企业专属队列
        List<String> enterpriseQueues = getEnterpriseQueues();
        for (String queueName : enterpriseQueues) {
            // 为每个企业专属队列创建一个消费路由
            from(String.format("rabbitmq://%s:%d?username=%s&password=%s%s" 
                            + "&queue=%s"                       // 企业专属队列
                            + "&declare=%b"                     // 队列已存在，不需要自动声明
                            + "&durable=%b"                     // 队列持久化
                            + "&autoAck=%b"                    // 关闭自动ACK（核心）
                            + "&concurrentConsumers=%d"         // 单实例消费线程数
                            + "&prefetchCount=%d"               // 每个线程预取消息数
                            + "&requestTimeout=%d",             // 消费者超时时间
                    rabbitHost, rabbitPort, rabbitUsername, rabbitPassword, 
                    rabbitVirtualHost.isEmpty() ? "" : "&vhost=" + rabbitVirtualHost,
                    queueName,
                    rabbitmqConsumerDeclare,
                    rabbitmqConsumerDurable,
                    rabbitmqConsumerAutoAck,
                    rabbitmqConsumerConcurrentConsumers,
                    rabbitmqConsumerPrefetchCount,
                    rabbitmqConsumerRequestTimeout))
                    .routeId("cbec-msg-consumer-" + queueName + "-" + System.currentTimeMillis()) // 多实例路由ID唯一
                    .description("消费企业专属队列消息，按MessageType转发到Kafka: " + queueName)

                    // 步骤1：提取消息唯一ID，做幂等校验
                    // .bean(EnterpriseMsgManager.class, "checkIdempotent")
                    // .choice()
                    //     .when(header("IS_PROCESSED").isEqualTo(true))
                    //         .bean(EnterpriseMsgManager.class, "manualAck") // 已处理过，直接ACK
                    //         .stop() // 终止流程
                    // .end()

                    // 步骤2：提取MessageType（CBEC001/CBEC002/CBEC003）
                    .bean(EnterpriseMsgManager.class, "extractMessageType")
                    
                    // 步骤2.1：将RabbitMQ消息的header信息复制到Camel Exchange中，以便传递到Kafka
                    .process(exchange -> {
                        // 获取RabbitMQ消息的header信息
                        java.util.Map<String, Object> rabbitHeaders = exchange.getIn().getHeaders();
                        if (rabbitHeaders != null && !rabbitHeaders.isEmpty()) {
                            // 遍历所有header，将非Camel系统header复制到Exchange中
                            for (java.util.Map.Entry<String, Object> entry : rabbitHeaders.entrySet()) {
                                String key = entry.getKey();
                                // 排除Camel系统header（以Camel或org.apache.camel开头的header）
                                if (!key.startsWith("Camel") && !key.startsWith("org.apache.camel")) {
                                    exchange.getIn().setHeader(key, entry.getValue());
                                }
                            }
                        }
                        
                        // 构建Kafka消息的key值：{SenderID}-{MessageType}-{MessageID}
                        String senderId = (String) exchange.getIn().getHeader(kafkaKeyHeaderSenderId);
                        String messageType = (String) exchange.getIn().getHeader(kafkaKeyHeaderMessageType);
                        String messageId = (String) exchange.getIn().getHeader(kafkaKeyHeaderMessageId);
                        
                        if (senderId != null && messageType != null && messageId != null) {
                            String kafkaKey = senderId + kafkaKeySeparator + messageType + kafkaKeySeparator + messageId;
                            exchange.getIn().setHeader(KafkaConstants.KEY, kafkaKey);
                        }
                    })

                    // 步骤3：按MessageType路由到对应Kafka Topic
                    .choice()
                        .when(header("MessageType").isEqualTo(messageTypeOrder))
                            .to(String.format("kafka:%s?brokers=%s", kafkaTopicOrder, kafkaServers))
                        .when(header("MessageType").isEqualTo(messageTypeWaybill))
                            .to(String.format("kafka:%s?brokers=%s", kafkaTopicWaybill, kafkaServers))
                        .when(header("MessageType").isEqualTo(messageTypePayment))
                            .to(String.format("kafka:%s?brokers=%s", kafkaTopicPayment, kafkaServers))
                        // 未知MessageType，转发到专属队列
                        .otherwise()
                            .to(String.format("rabbitmq:%s?hostname=%s&portNumber=%d&username=%s&password=%s%s&exchangeType=topic&queue=%s&routingKey=enterprise.unknown.#&declare=false",
                                    rabbitmqExchange, rabbitHost, rabbitPort, rabbitUsername, rabbitPassword, 
                                    rabbitVirtualHost.isEmpty() ? "" : "&vhost=" + rabbitVirtualHost,
                                    rabbitmqUnknownQueue))
                    .end()

                    // 步骤4：手动ACK，标记消息已消费
                    .bean(EnterpriseMsgManager.class, "manualAck");
        }
    }
    
    /**
     * 获取所有企业专属队列
     * @return 企业专属队列列表
     * @throws Exception 异常
     */
    private List<String> getEnterpriseQueues() throws Exception {
        // ConnectionFactory factory = new ConnectionFactory();
        // factory.setHost(rabbitHost);
        // factory.setPort(rabbitPort);
        // factory.setUsername(rabbitUsername);
        // factory.setPassword(rabbitPassword);
        // if (!rabbitVirtualHost.isEmpty()) {
        //     factory.setVirtualHost(rabbitVirtualHost);
        // }

        List<String> enterpriseQueues = new java.util.ArrayList<>();
        
        // 模拟获取企业专属队列
        // 在实际应用中，这里应该使用RabbitMQ的API来获取所有队列，然后过滤出企业专属队列
        // 由于RabbitMQ Java客户端没有直接获取所有队列的API，我们可以使用以下方法：
        // 1. 使用RabbitMQ Management API
        // 2. 维护一个队列注册表
        // 3. 定期扫描已知的队列前缀
        
        // 这里我们模拟一些企业专属队列
        enterpriseQueues.add("queue.enterprise.000001");
        enterpriseQueues.add("queue.enterprise.000002");
        enterpriseQueues.add("queue.enterprise.000003");
        
        return enterpriseQueues;
    }
}