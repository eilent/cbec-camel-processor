package com.enterprise.msg.api;

import com.enterprise.msg.manager.EnterpriseQueueManager;
import com.enterprise.msg.util.RabbitMqSenderUtil;
import com.enterprise.msg.util.XmlParserUtil;
import org.dom4j.DocumentException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;



/**
 * 订单API控制器
 * 用于接收企业发送的订单数据报文，保存至Kafka后由Flink处理
 */
@RestController
@RequestMapping("/api")
@Slf4j
@Tag(name = "订单管理", description = "订单相关的API接口")
public class RouteManagerController {

    @Autowired
    private EnterpriseQueueManager enterpriseQueueManager;

    @Autowired
    private RabbitMqSenderUtil rabbitMqSenderUtil;

	// 企业接入接口
	@PostMapping("/route/createmq")
	public ResponseEntity<String> routeMqCreate(@RequestParam String enterpriseId) {
		try {
			// 创建并绑定企业队列
			enterpriseQueueManager.createAndBindQueue(enterpriseId);
			return ResponseEntity.ok("企业" + enterpriseId + "接入成功，专属队列已创建并绑定");
		} catch (Exception e) {
			return ResponseEntity.status(500).body("接入失败：" + e.getMessage());
		}
	}

    // 企业消息发送接口
    @PostMapping("/route/sendmessage")
    public ResponseEntity<String> sendEnterpriseMessage(@RequestBody EnterpriseMessageRequest request) {
        try {
            // 验证请求参数
            if (request == null || request.getEnterpriseId() == null || request.getMessageContents() == null || request.getMessageContents().isEmpty()) {
                return ResponseEntity.status(400).body("请求参数不完整，请提供企业ID和报文内容");
            }

            if (request.getExchangeName() == null || request.getRoutingKey() == null) {
                return ResponseEntity.status(400).body("请求参数不完整，请提供RabbitMQ交换器名称和路由键");
            }

            List<String> messageContents = request.getMessageContents();
            String exchangeName = request.getExchangeName();
            String routingKey = request.getRoutingKey();

            // 循环处理全部报文集合
            for (int i = 0; i < messageContents.size(); i++) {
                String xmlContent = messageContents.get(i);
                try {
                    // 解析XML报文，提取消息头节点信息
                    Map<String, String> headerMap = XmlParserUtil.parseMessageHeader(xmlContent);

                    // 判断报文是否成功提取到消息头信息
                    if (XmlParserUtil.isValidHeader(headerMap)) {
                        // 消息头信息完整，将消息发送到RabbitMQ，同时将消息头写入到消息属性中
                        rabbitMqSenderUtil.sendMessage(exchangeName, routingKey, xmlContent, headerMap);
                        log.info("企业{}的第{}条报文发送成功，MessageID: {}", request.getEnterpriseId(), i + 1, headerMap.get("MessageID"));
                    } else {
                        // 消息头信息不完整，返回错误信息
                        return ResponseEntity.status(400).body("第" + (i + 1) + "条报文消息头信息不完整，缺少必要的字段");
                    }
                } catch (DocumentException e) {
                    // XML解析异常，返回错误信息
                    return ResponseEntity.status(400).body("第" + (i + 1) + "条报文XML格式错误：" + e.getMessage());
                } catch (Exception e) {
                    // 其他异常，返回错误信息
                    return ResponseEntity.status(500).body("第" + (i + 1) + "条报文处理失败：" + e.getMessage());
                }
            }

            return ResponseEntity.ok("企业" + request.getEnterpriseId() + "的" + messageContents.size() + "条报文全部处理成功");
        } catch (Exception e) {
            log.error("处理企业消息失败：", e);
            return ResponseEntity.status(500).body("处理企业消息失败：" + e.getMessage());
        }
    }
}