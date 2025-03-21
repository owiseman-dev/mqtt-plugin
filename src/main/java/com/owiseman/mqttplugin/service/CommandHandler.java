package com.owiseman.mqttplugin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class CommandHandler {

    private static final Logger logger = LoggerFactory.getLogger(CommandHandler.class);

    @Autowired
    private MqttService mqttService;

    public Map<String, Object> handleCommand(String command, Map<String, String> parameters) {
        logger.info("Handling command: {} with parameters: {}", command, parameters);
        
        Map<String, Object> result = new HashMap<>();
        
        switch (command.toLowerCase()) {
            case "publish":
                return handlePublish(parameters);
            case "status":
                return handleStatus();
            case "restart":
                return handleRestart();
            default:
                result.put("success", false);
                result.put("error", "Unknown command: " + command);
                return result;
        }
    }

    private Map<String, Object> handlePublish(Map<String, String> parameters) {
        Map<String, Object> result = new HashMap<>();
        
        String topic = parameters.get("topic");
        String message = parameters.get("message");
        String qosStr = parameters.get("qos");
        
        if (topic == null || message == null) {
            result.put("success", false);
            result.put("error", "Missing required parameters: topic and message");
            return result;
        }
        
        int qos = 0;
        if (qosStr != null) {
            try {
                qos = Integer.parseInt(qosStr);
                if (qos < 0 || qos > 2) {
                    qos = 0;
                }
            } catch (NumberFormatException e) {
                // 使用默认值
            }
        }
        
        try {
            mqttService.publishMessage(topic, message, qos);
            result.put("success", true);
            result.put("message", "Message published to topic: " + topic);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Failed to publish message: " + e.getMessage());
        }
        
        return result;
    }

    private Map<String, Object> handleStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("running", mqttService.isRunning());
        result.put("uptime", mqttService.getUptime());
        return result;
    }

    private Map<String, Object> handleRestart() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (mqttService.isRunning()) {
                mqttService.stop();
            }
            mqttService.start();
            result.put("success", true);
            result.put("message", "MQTT broker restarted successfully");
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", "Failed to restart MQTT broker: " + e.getMessage());
        }
        
        return result;
    }
}