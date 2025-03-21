package com.owiseman.mqttplugin.controller;

import com.owiseman.mqttplugin.service.MqttService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mqtt")
public class MqttController {

    @Autowired
    private MqttService mqttService;

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("running", mqttService.isRunning());
        status.put("uptime", mqttService.getUptime());
        
        return ResponseEntity.ok(status);
    }

    @PostMapping("/publish")
    public ResponseEntity<Map<String, String>> publishMessage(
            @RequestParam String topic,
            @RequestParam String message,
            @RequestParam(defaultValue = "0") int qos) {
        
        mqttService.publishMessage(topic, message, qos);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "Message published to topic: " + topic);
        
        return ResponseEntity.ok(response);
    }
}