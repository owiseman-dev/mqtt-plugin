package com.owiseman.mqttplugin.service;

import com.owiseman.dataapi.proto.*;
import com.owiseman.mqttplugin.config.MqttConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PluginGrpcService {

    private static final Logger logger = LoggerFactory.getLogger(PluginGrpcService.class);

    private final MqttConfig mqttConfig;
    private final MqttService mqttService;
    private ManagedChannel channel;
    private PluginServiceGrpc.PluginServiceBlockingStub blockingStub;
    private String pluginId;

    @Autowired
    public PluginGrpcService(MqttConfig mqttConfig, MqttService mqttService) {
        this.mqttConfig = mqttConfig;
        this.mqttService = mqttService;
        initGrpcChannel();
    }

    private void initGrpcChannel() {
        logger.info("Initializing gRPC channel to {}:{}", mqttConfig.getDataApiHost(), mqttConfig.getDataApiPort());
        channel = ManagedChannelBuilder.forAddress(mqttConfig.getDataApiHost(), mqttConfig.getDataApiPort())
                .usePlaintext()
                .build();
        blockingStub = PluginServiceGrpc.newBlockingStub(channel);
    }

    public void registerPlugin() {
        logger.info("Registering plugin to Data API");
        
        PluginRegistration request = PluginRegistration.newBuilder()
                .setName(mqttConfig.getPluginName())
                .setVersion(mqttConfig.getPluginVersion())
                .setType(mqttConfig.getPluginType())
                .setDescription(mqttConfig.getPluginDescription())
                .setHost(mqttConfig.getPluginHost())
                .setPort(mqttConfig.getPluginPort())
                .build();
        
        try {
            RegistrationResponse response = blockingStub.registerPlugin(request);
            
            if (response.getSuccess()) {
                this.pluginId = response.getPluginId();
                logger.info("Plugin registered successfully with ID: {}", pluginId);
            } else {
                logger.error("Failed to register plugin: {}", response.getMessage());
            }
        } catch (Exception e) {
            logger.error("Error registering plugin", e);
        }
    }

    @Scheduled(fixedRate = 30000) // 每30秒发送一次心跳
    public void sendHeartbeat() {
        if (pluginId == null) {
            logger.warn("Cannot send heartbeat, plugin not registered");
            return;
        }
        
        logger.debug("Sending heartbeat to Data API");
        
        HeartbeatRequest request = HeartbeatRequest.newBuilder()
                .setPluginId(pluginId)
                .setStatusInfo("RUNNING")
                .build();
        
        try {
            HeartbeatResponse response = blockingStub.heartbeat(request);
            logger.debug("Heartbeat response received: {}", response.getReceived());
        } catch (Exception e) {
            logger.error("Error sending heartbeat", e);
        }
    }

    public StatusResponse getStatus() {
        if (pluginId == null) {
            logger.warn("Cannot get status, plugin not registered");
            return null;
        }
        
        StatusRequest request = StatusRequest.newBuilder()
                .setPluginId(pluginId)
                .build();
        
        try {
            return blockingStub.getStatus(request);
        } catch (Exception e) {
            logger.error("Error getting status", e);
            return null;
        }
    }

    // 在类定义中添加CommandHandler依赖
    @Autowired
    private CommandHandler commandHandler;
    
    // 修改executeCommand方法
    public CommandResponse executeCommand(String command, Map<String, String> parameters) {
        if (pluginId == null) {
            logger.warn("Cannot execute command, plugin not registered");
            return null;
        }
        
        try {
            // 处理命令
            Map<String, Object> result = commandHandler.handleCommand(command, parameters);
            boolean success = (boolean) result.getOrDefault("success", false);
            
            CommandResponse.Builder responseBuilder = CommandResponse.newBuilder()
                    .setSuccess(success);
            
            if (success) {
                String message = (String) result.getOrDefault("message", "Command executed successfully");
                responseBuilder.setResult(message);
            } else {
                String error = (String) result.getOrDefault("error", "Unknown error");
                responseBuilder.setErrorMessage(error);
            }
            
            return responseBuilder.build();
        } catch (Exception e) {
            logger.error("Error executing command", e);
            return CommandResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("Error: " + e.getMessage())
                    .build();
        }
    }

    public void stopPlugin() {
        if (pluginId == null) {
            logger.warn("Cannot stop plugin, plugin not registered");
            return;
        }
        
        StopRequest request = StopRequest.newBuilder()
                .setPluginId(pluginId)
                .build();
        
        try {
            StopResponse response = blockingStub.stopPlugin(request);
            logger.info("Stop plugin response: {}", response.getMessage());
        } catch (Exception e) {
            logger.error("Error stopping plugin", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        logger.info("Shutting down gRPC channel");
        try {
            stopPlugin();
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.error("Error shutting down gRPC channel", e);
        }
    }
}