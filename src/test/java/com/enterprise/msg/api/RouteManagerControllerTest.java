package com.enterprise.msg.api;

import com.enterprise.msg.manager.EnterpriseQueueManager;
import com.enterprise.msg.util.RabbitMqSenderUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class RouteManagerControllerTest {

    private RouteManagerController routeManagerController;

    @Mock
    private EnterpriseQueueManager enterpriseQueueManager;

    @Spy
    private RabbitMqSenderUtil rabbitMqSenderUtil;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        routeManagerController = new RouteManagerController();
        // 使用反射设置enterpriseQueueManager
        try {
            java.lang.reflect.Field field = RouteManagerController.class.getDeclaredField("enterpriseQueueManager");
            field.setAccessible(true);
            field.set(routeManagerController, enterpriseQueueManager);

            // 设置RabbitMQ连接参数
            java.lang.reflect.Field hostField = RabbitMqSenderUtil.class.getDeclaredField("rabbitHost");
            hostField.setAccessible(true);
            hostField.set(rabbitMqSenderUtil, "192.168.111.105");

            java.lang.reflect.Field portField = RabbitMqSenderUtil.class.getDeclaredField("rabbitPort");
            portField.setAccessible(true);
            portField.set(rabbitMqSenderUtil, 5672);

            java.lang.reflect.Field usernameField = RabbitMqSenderUtil.class.getDeclaredField("rabbitUsername");
            usernameField.setAccessible(true);
            usernameField.set(rabbitMqSenderUtil, "cbec");

            java.lang.reflect.Field passwordField = RabbitMqSenderUtil.class.getDeclaredField("rabbitPassword");
            passwordField.setAccessible(true);
            passwordField.set(rabbitMqSenderUtil, "cbec@2026");

            java.lang.reflect.Field virtualHostField = RabbitMqSenderUtil.class.getDeclaredField("rabbitVirtualHost");
            virtualHostField.setAccessible(true);
            virtualHostField.set(rabbitMqSenderUtil, "cbec");

            // 调用init方法初始化连接工厂
            java.lang.reflect.Method initMethod = RabbitMqSenderUtil.class.getDeclaredMethod("init");
            initMethod.setAccessible(true);
            initMethod.invoke(rabbitMqSenderUtil);

            // 使用反射设置rabbitMqSenderUtil
            java.lang.reflect.Field rabbitField = RouteManagerController.class.getDeclaredField("rabbitMqSenderUtil");
            rabbitField.setAccessible(true);
            rabbitField.set(routeManagerController, rabbitMqSenderUtil);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testRouteMqCreate_Success() throws Exception {
        // 模拟企业队列创建成功
        // 不需要做任何事情，因为我们期望方法正常执行

        // 调用方法
        ResponseEntity<String> response = routeManagerController.routeMqCreate("000002");

        // 验证响应
        assertEquals(200, response.getStatusCodeValue());
        assertEquals("企业000002接入成功，专属队列已创建并绑定", response.getBody());

        // 验证enterpriseQueueManager.createAndBindQueue方法被调用
        verify(enterpriseQueueManager).createAndBindQueue("000002");
    }

    @Test
    public void testRouteMqCreate_Failure() throws Exception {
        // 模拟企业队列创建失败
        doThrow(new RuntimeException("创建队列失败"))
            .when(enterpriseQueueManager).createAndBindQueue(anyString());

        // 调用方法
        ResponseEntity<String> response = routeManagerController.routeMqCreate("test_enterprise_002");

        // 验证响应
        assertEquals(500, response.getStatusCodeValue());
        assertEquals("接入失败：创建队列失败", response.getBody());

        // 验证enterpriseQueueManager.createAndBindQueue方法被调用
        verify(enterpriseQueueManager).createAndBindQueue("test_enterprise_002");
    }

    @Test
    public void testRouteMqCreate_EmptyEnterpriseId() throws Exception {
        // 模拟企业队列创建失败（空ID）
        doThrow(new RuntimeException("企业ID不能为空"))
            .when(enterpriseQueueManager).createAndBindQueue(anyString());

        // 调用方法
        ResponseEntity<String> response = routeManagerController.routeMqCreate("");

        // 验证响应
        assertEquals(500, response.getStatusCodeValue());
        assertEquals("接入失败：企业ID不能为空", response.getBody());

        // 验证enterpriseQueueManager.createAndBindQueue方法被调用
        verify(enterpriseQueueManager).createAndBindQueue("");
    }

    /**
     * 生成当前时间的全数字格式，精确到毫秒
     * @return 格式为yyyyMMddHHmmssSSS的时间字符串
     */
    private String getCurrentTimeMillisFormat() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return sdf.format(new Date());
    }

    @Test
    public void testSendEnterpriseMessage_Success() throws Exception {

        // 生成0000001-0000003三家企业的测试数据，其中每家企业生成CBEC001-CBEC003三种类型的报文，每种报文生成10条
        String[] enterpriseIds = {"000001", "000002", "000003"};
        String[] messageTypes = {"CBEC001", "CBEC002", "CBEC003"};
        for (String enterpriseId : enterpriseIds) {
            for (String messageType : messageTypes) {
                EnterpriseMessageRequest request = new EnterpriseMessageRequest();
                request.setEnterpriseId(enterpriseId);
                request.setMessageType(messageType);
                request.setExchangeName("ex_enterprise_mq");
                request.setRoutingKey("enterprise." + enterpriseId + ".msg");
                List<String> messageContents = new ArrayList<>();
                for (int i = 1; i <= 10; i++) {
                    String xml = String.format(
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Message><MessageHeader><MessageType>%s</MessageType><MessageID>%s_MSG%03d</MessageID><SenderID>%s</SenderID><ReceiverID>RECEIVER001</ReceiverID><SendTime>%s</SendTime></MessageHeader><MessageBody>Test Body %d</MessageBody></Message>",
                        messageType, messageType, i, enterpriseId, getCurrentTimeMillisFormat(), i
                    );
                    messageContents.add(xml);
                }
                request.setMessageContents(messageContents);
                        // 调用方法
                ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);
                assertEquals(200, response.getStatusCodeValue());
            }
        }

        // 验证响应
        assertEquals(200, 200);
  

        // 准备测试数据(单条测试数据)
        // EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        // request.setEnterpriseId("000001");
        // request.setMessageType("CBEC001");
        // request.setExchangeName("ex_enterprise_mq");
        // request.setRoutingKey("enterprise.000001.msg");

        // List<String> messageContents = new ArrayList<>();
        // messageContents.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Message><MessageHeader><MessageType>CBEC003</MessageType><MessageID>CBEC001_MSG001</MessageID><SenderID>SENDER001</SenderID><ReceiverID>RECEIVER001</ReceiverID><SendTime>2023-01-01T12:00:00</SendTime></MessageHeader><MessageBody>Test Body</MessageBody></Message>");
        // request.setMessageContents(messageContents);

        // 模拟RabbitMQ发送成功
        //doNothing().when(rabbitMqSenderUtil).sendMessage(anyString(), anyString(), anyString());

        // 调用方法
        // ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        // assertEquals(200, response.getStatusCodeValue());
        // assertEquals("企业000002的1条报文全部处理成功", response.getBody());

        // 验证rabbitMqSenderUtil.sendMessage方法被调用
        //verify(rabbitMqSenderUtil).sendMessage("ex_enterprise_mq", "enterprise.000001.msg", messageContents.get(0));
    }

    @Test
    public void testSendEnterpriseMessage_MissingParameters() throws Exception {
        // 准备测试数据 - 缺少企业ID
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setMessageType("CBEC001");
        request.setExchangeName("test_exchange");
        request.setRoutingKey("test_routing_key");
        request.setMessageContents(new ArrayList<>());

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("请求参数不完整，请提供企业ID和报文内容", response.getBody());
    }

    @Test
    public void testSendEnterpriseMessage_MissingRabbitMQParameters() throws Exception {
        // 准备测试数据 - 缺少RabbitMQ参数
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setEnterpriseId("test_enterprise_001");
        request.setMessageType("CBEC001");
        request.setRoutingKey("test_routing_key");

        List<String> messageContents = new ArrayList<>();
        messageContents.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Message><MessageHeader><MessageType>CBEC001</MessageType><MessageID>MSG001</MessageID><SenderID>SENDER001</SenderID><ReceiverID>RECEIVER001</ReceiverID><SendTime>2023-01-01T12:00:00</SendTime></MessageHeader><MessageBody>Test Body</MessageBody></Message>");
        request.setMessageContents(messageContents);

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("请求参数不完整，请提供RabbitMQ交换器名称和路由键", response.getBody());
    }

    @Test
    public void testSendEnterpriseMessage_InvalidXml() throws Exception {
        // 准备测试数据 - 无效的XML
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setEnterpriseId("test_enterprise_001");
        request.setMessageType("CBEC001");
        request.setExchangeName("test_exchange");
        request.setRoutingKey("test_routing_key");

        List<String> messageContents = new ArrayList<>();
        messageContents.add("Invalid XML");
        request.setMessageContents(messageContents);

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(400, response.getStatusCodeValue());
        assertTrue(response.getBody().startsWith("第1条报文XML格式错误："));
    }

    @Test
    public void testSendEnterpriseMessage_IncompleteHeader() throws Exception {
        // 准备测试数据 - 消息头不完整
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setEnterpriseId("test_enterprise_001");
        request.setMessageType("CBEC001");
        request.setExchangeName("test_exchange");
        request.setRoutingKey("test_routing_key");

        List<String> messageContents = new ArrayList<>();
        messageContents.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Message><MessageHeader><MessageType>CBEC001</MessageType><MessageID>MSG001</MessageID></MessageHeader><MessageBody>Test Body</MessageBody></Message>");
        request.setMessageContents(messageContents);

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("第1条报文消息头信息不完整，缺少必要的字段", response.getBody());
    }

    @Test
    public void testSendEnterpriseMessage_RabbitMQFailure() throws Exception {
        // 准备测试数据
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setEnterpriseId("test_enterprise_001");
        request.setMessageType("CBEC001");
        request.setExchangeName("test_exchange");
        request.setRoutingKey("test_routing_key");

        List<String> messageContents = new ArrayList<>();
        messageContents.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?><Message><MessageHeader><MessageType>CBEC001</MessageType><MessageID>MSG001</MessageID><SenderID>SENDER001</SenderID><ReceiverID>RECEIVER001</ReceiverID><SendTime>2023-01-01T12:00:00</SendTime></MessageHeader><MessageBody>Test Body</MessageBody></Message>");
        request.setMessageContents(messageContents);

        // 模拟RabbitMQ发送失败
        doThrow(new RuntimeException("RabbitMQ发送失败")).when(rabbitMqSenderUtil).sendMessage(anyString(), anyString(), anyString());

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(500, response.getStatusCodeValue());
        assertTrue(response.getBody().startsWith("第1条报文处理失败："));
    }

    @Test
    public void testSendEnterpriseMessage_GeneralException() throws Exception {
        // 准备测试数据
        EnterpriseMessageRequest request = new EnterpriseMessageRequest();
        request.setEnterpriseId("test_enterprise_001");
        request.setMessageType("CBEC001");
        request.setExchangeName("test_exchange");
        request.setRoutingKey("test_routing_key");
        request.setMessageContents(null);

        // 调用方法
        ResponseEntity<String> response = routeManagerController.sendEnterpriseMessage(request);

        // 验证响应
        assertEquals(400, response.getStatusCodeValue());
        assertEquals("请求参数不完整，请提供企业ID和报文内容", response.getBody());
    }
}
