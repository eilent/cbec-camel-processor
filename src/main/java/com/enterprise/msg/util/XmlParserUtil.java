package com.enterprise.msg.util;

import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * XML解析工具类
 * 用于解析XML格式的报文，提取消息头节点信息
 */
public class XmlParserUtil {

    /**
     * 解析XML报文，提取消息头节点信息
     * @param xmlString XML格式的报文内容
     * @return 包含消息头节点信息的Map
     * @throws DocumentException XML解析异常
     */
    public static Map<String, String> parseMessageHeader(String xmlString) throws DocumentException {
        Map<String, String> headerMap = new HashMap<>();

        // 创建SAXReader对象
        SAXReader reader = new SAXReader();

        // 解析XML字符串
        Document document = reader.read(new StringReader(xmlString));

        // 获取根元素
        Element rootElement = document.getRootElement();

        // 查找消息头节点
        Element headerElement = rootElement.element("MessageHeader");
        if (headerElement != null) {
            // 提取MessageType
            Element messageTypeElement = headerElement.element("MessageType");
            if (messageTypeElement != null) {
                headerMap.put("MessageType", messageTypeElement.getText());
            }

            // 提取MessageID
            Element messageIDElement = headerElement.element("MessageID");
            if (messageIDElement != null) {
                headerMap.put("MessageID", messageIDElement.getText());
            }

            // 提取SenderID
            Element senderIDElement = headerElement.element("SenderID");
            if (senderIDElement != null) {
                headerMap.put("SenderID", senderIDElement.getText());
            }

            // 提取ReceiverID
            Element receiverIDElement = headerElement.element("ReceiverID");
            if (receiverIDElement != null) {
                headerMap.put("ReceiverID", receiverIDElement.getText());
            }

            // 提取SendTime
            Element sendTimeElement = headerElement.element("SendTime");
            if (sendTimeElement != null) {
                headerMap.put("SendTime", sendTimeElement.getText());
            }
        }

        return headerMap;
    }

    /**
     * 验证消息头节点信息是否完整
     * @param headerMap 包含消息头节点信息的Map
     * @return 是否完整
     */
    public static boolean isValidHeader(Map<String, String> headerMap) {
        return headerMap.containsKey("MessageType") &&
               headerMap.containsKey("MessageID") &&
               headerMap.containsKey("SenderID") &&
               headerMap.containsKey("ReceiverID") &&
               headerMap.containsKey("SendTime");
    }
}
