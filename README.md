# CBEC Camel Processor

企业消息处理系统，基于Apache Camel、RabbitMQ、Kafka和Redis的消息路由与处理框架。

## 项目概述

CBEC Camel Processor是一个企业级消息处理系统，主要用于处理和路由企业消息，支持从RabbitMQ读取消息并根据消息类型转发到对应的Kafka主题。系统采用Apache Camel作为消息路由引擎，实现了消息的可靠传递和处理。

## 功能特性

- **消息路由**：基于消息类型自动路由到对应的Kafka主题
- **动态队列消费**：自动扫描并消费企业专属队列
- **消息头传递**：将消息头信息从RabbitMQ传递到Kafka
- **Kafka消息key构建**：基于消息头构建Kafka消息key
- **异常处理**：消息处理失败后自动进入死信队列
- **手动消息确认**：支持消息的手动确认，确保消息处理的可靠性
- **可配置化**：关键参数可通过配置文件调整，无需修改代码

## 技术栈

- **Spring Boot 2.7.15**：应用框架
- **Apache Camel 3.20.7**：消息路由引擎
- **RabbitMQ**：消息队列
- **Kafka**：消息流平台
- **Redis**：缓存（可选）
- **Springdoc OpenAPI**：API文档

## 快速开始

### 环境要求

- JDK 11+
- Maven 3.6+
- RabbitMQ 3.8+
- Kafka 2.8+
- Redis 6.0+（可选）

### 安装与运行

1. **克隆项目**

```bash
git clone <项目地址>
cd cbec-camel-processor
```

2. **配置环境变量**

修改 `src/main/resources/application.yml` 文件，配置RabbitMQ、Kafka等服务的连接信息。

3. **构建项目**

```bash
mvn clean package
```

4. **运行项目**

```bash
java -jar target/cbec-camel-processor-1.0.0.jar
```

## 目录结构

```
cbec-camel-processor/
├── src/
│   ├── main/
│   │   ├── java/com/enterprise/msg/
│   │   │   ├── api/            # REST API控制器
│   │   │   ├── manager/         # 消息管理类
│   │   │   ├── route/           # Camel路由配置
│   │   │   ├── util/            # 工具类
│   │   │   └── Application.java # 应用入口
│   │   └── resources/
│   │       └── application.yml  # 配置文件
│   └── test/                    # 测试代码
├── pom.xml                      # Maven配置
└── README.md                    # 项目文档
```

## 核心组件

### 1. RouteManagerController

提供REST API接口，用于企业接入和消息发送。

- **POST /api/route/createmq**：创建企业专属队列
- **POST /api/route/sendmessage**：发送企业消息

### 2. EnterpriseMsgRoute

Camel路由配置，负责从RabbitMQ读取消息并转发到Kafka。

- 动态扫描企业专属队列
- 根据消息类型路由到对应的Kafka主题
- 构建Kafka消息key
- 处理异常消息

### 3. RabbitMqSenderUtil

RabbitMQ消息发送工具类，支持发送消息到指定的交换器和路由键。

### 4. EnterpriseQueueManager

企业队列管理类，负责创建和绑定企业专属队列。

## 配置说明

### RabbitMQ配置

```yaml
rabbitmq:
  host: 192.168.111.105
  port: 5672
  username: cbec
  password: cbec@2026
  virtual-host: cbec
```

### Kafka配置

```yaml
kafka:
  bootstrap-servers: 192.168.120.8:9092,192.168.120.9:9092,192.168.120.10:9092
  consumer:
    group-id: cbec-consumer-group
    auto-offset-reset: earliest
    enable-auto-commit: false
  producer:
    acks: all
```

### 企业消息配置

```yaml
enterprise:
  msg:
    rabbitmq:
      exchange: ex_enterprise_mq
      consumer:
        concurrent-consumers: 5
        prefetch-count: 2
    kafka:
      key:
        header-sender-id: SenderID
        header-message-type: MessageType
        header-message-id: MessageID
        separator: "-"
      topic:
        order: cbec_topic_order
        waybill: cbec_topic_waybill
        payment: cbec_topic_payment
    message-type:
      order: CBEC001
      waybill: CBEC002
      payment: CBEC003
```

## API文档

项目使用Springdoc OpenAPI生成API文档，启动项目后可访问：

```
http://localhost:8080/swagger-ui.html
```

## 消息格式

### 输入消息格式（XML）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Message>
  <MessageHeader>
    <MessageType>CBEC001</MessageType> <!-- 消息类型：CBEC001(订单)、CBEC002(运单)、CBEC003(支付) -->
    <MessageID>MSG001</MessageID>       <!-- 消息唯一标识 -->
    <SenderID>SENDER001</SenderID>     <!-- 发送方ID -->
    <ReceiverID>RECEIVER001</ReceiverID> <!-- 接收方ID -->
    <SendTime>20260228123456789</SendTime> <!-- 发送时间，格式：yyyyMMddHHmmssSSS -->
  </MessageHeader>
  <MessageBody>Test Body</MessageBody> <!-- 消息体内容 -->
</Message>
```

### Kafka消息格式

- **Key**：`{SenderID}-{MessageType}-{MessageID}`
- **Value**：原始XML消息内容
- **Headers**：从RabbitMQ传递的消息头信息

## 故障处理

1. **消息处理失败**：消息会进入死信队列 `queue_enterprise_dlx`
2. **未知消息类型**：消息会进入未知消息队列 `queue_enterprise_unknown`
3. **RabbitMQ连接失败**：系统会自动重试连接
4. **Kafka连接失败**：系统会自动重试发送

## 监控与日志

- **日志配置**：通过 `application.yml` 中的 `logging` 配置调整日志级别
- **Actuator**：Spring Boot Actuator提供了健康检查和监控端点

## 开发与调试

### 运行测试

```bash
mvn test
```

### 代码风格

- 遵循Java代码规范
- 使用Lombok简化代码
- 采用分层架构设计

## 部署建议

1. **生产环境**：建议使用容器化部署（Docker）
2. **高可用性**：建议部署多个实例，通过RabbitMQ和Kafka的集群保证高可用
3. **监控**：建议集成Prometheus和Grafana进行监控
4. **日志**：建议使用ELK Stack收集和分析日志

## 版本历史

- **v1.0.0**：初始版本，实现基本的消息路由和处理功能

## 贡献指南

1. Fork本项目
2. 创建特性分支
3. 提交更改
4. 推送到分支
5. 创建Pull Request

## 许可证

本项目采用Apache License 2.0许可证。

## 联系方式

- 项目维护者：[您的名字]
- 邮箱：[您的邮箱]
- 问题反馈：[GitHub Issues链接]
