package com.owiseman.mqttplugin.service;

import com.owiseman.dataapi.proto.*;
import com.owiseman.mqttplugin.config.MqttConfig;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
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

    @Scheduled(fixedRate = 60000) // 从30秒改为60秒发送一次心跳
    public void sendHeartbeat() {
        // 检查是否已初始化
        if (blockingStub == null) {
            logger.warn("blockingStub为空，尝试重新初始化gRPC通道");
            initGrpcChannel();
            
            // 如果初始化后仍为空，则返回
            if (blockingStub == null) {
                logger.error("无法初始化gRPC通道，跳过心跳发送");
                return;
            }
        }
        
        // 检查是否已注册
        if (pluginId == null || pluginId.isEmpty()) {
            logger.warn("插件ID为空，尝试注册插件");
            registerPlugin();
            
            // 如果注册后仍为空，则返回
            if (pluginId == null || pluginId.isEmpty()) {
                logger.error("插件注册失败，无法获取插件ID，跳过心跳发送");
                return;
            }
        }
        
        // 发送心跳
        try {
            logger.debug("发送心跳到服务器，插件ID: {}", pluginId);
            
            HeartbeatRequest request = HeartbeatRequest.newBuilder()
                    .setPluginId(pluginId)
                    .setStatusInfo(mqttService.isRunning() ? "RUNNING" : "STOPPED")
                    .build();
            
            HeartbeatResponse response = blockingStub.heartbeat(request);
            
            if (response.getReceived()) {
                logger.debug("心跳发送成功，服务器时间: {}", response.getServerTime());
            } else {
                logger.warn("心跳发送失败，服务器未确认接收");
            }
        } catch (Exception e) {
            logger.error("发送心跳时发生错误: {}", e.getMessage());
            
            // 如果是连接问题，尝试重新初始化
            if (e.getMessage().contains("UNAVAILABLE") || 
                e.getMessage().contains("Connection") || 
                e.getMessage().contains("transport")) {
                
                logger.info("检测到连接问题，尝试重新初始化gRPC通道");
                initGrpcChannel();
            }
        }
    }

    // 修改初始化方法，进一步调整keepalive设置
    private void initGrpcChannel() {
        try {
            logger.info("初始化gRPC通道到 {}:{}", mqttConfig.getDataApiHost(), mqttConfig.getDataApiPort());
            
            // 关闭旧通道
            if (channel != null && !channel.isShutdown()) {
                logger.info("关闭旧的gRPC通道");
                try {
                    channel.shutdownNow().awaitTermination(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    logger.warn("关闭旧通道时中断", e);
                }
            }
            
            // 创建新通道，进一步调整keepalive设置
            channel = ManagedChannelBuilder.forAddress(mqttConfig.getDataApiHost(), mqttConfig.getDataApiPort())
                    .usePlaintext()
                    // 完全禁用客户端keepalive，避免发送ping
//                    .disableKeepAlive()// 禁用keepalive
                    .maxInboundMessageSize(10 * 1024 * 1024)  // 10MB
                    .build();
                
            blockingStub = PluginServiceGrpc.newBlockingStub(channel)
                    .withMaxInboundMessageSize(10 * 1024 * 1024);  // 10MB
                
            logger.info("gRPC通道初始化成功");
            
            // 立即尝试注册，确保连接有效
            if (pluginId == null || pluginId.isEmpty()) {
                registerPlugin();
            }
        } catch (Exception e) {
            logger.error("初始化gRPC通道时发生错误: {}", e.getMessage());
            channel = null;
            blockingStub = null;
        }
    }

    // 修改注册方法，增加错误处理和重试逻辑
    // 修改注册方法
    public void registerPlugin() {
        if (blockingStub == null) {
            logger.warn("blockingStub为空，尝试重新初始化gRPC通道");
            initGrpcChannel();
            
            if (blockingStub == null) {
                logger.error("无法初始化gRPC通道，跳过插件注册");
                return;
            }
        }
        
        try {
            String pluginName = mqttConfig.getPluginName();
            logger.info("开始注册插件: {}", pluginName);
            
            // 先尝试通过名称查找插件
            GetPluginByNameRequest findRequest = GetPluginByNameRequest.newBuilder()
                    .setName(pluginName)
                    .build();
            
            try {
                GetPluginByNameResponse findResponse = blockingStub.getPluginByName(findRequest);
                if (findResponse.hasPlugin()) {
                    pluginId = findResponse.getPlugin().getPluginId();
                    logger.info("找到现有插件: {}, ID: {}", pluginName, pluginId);
                    return;
                }
            } catch (Exception e) {
                logger.warn("查找插件时出错: {}", e.getMessage());
            }
            
            // 如果找不到，则注册新插件
            // 确保提供正确的主机和端口
            String hostAddress = mqttConfig.getHost();
            if ("0.0.0.0".equals(hostAddress)) {
                hostAddress = "localhost"; // 如果绑定到所有接口，使用localhost作为注册地址
            }
            
            PluginRegistration registration = PluginRegistration.newBuilder()
                    .setName(pluginName)
                    .setVersion(mqttConfig.getPluginVersion())
                    .setType("MQTT")
                    .setDescription("MQTT消息代理插件")
                    .setHost(hostAddress)
                    .setPort(mqttConfig.getPluginGrpcPort()) // 确保使用正确的gRPC端口
                    .build();
            
            RegistrationResponse response = blockingStub.registerPlugin(registration);
            
            if (response.getSuccess()) {
                pluginId = response.getPluginId();
                logger.info("插件注册成功，ID: {}", pluginId);
            } else {
                logger.error("插件注册失败: {}", response.getMessage());
            }
        } catch (Exception e) {
            logger.error("注册插件时发生错误: {}", e.getMessage());
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

    // 添加一个发送消息的方法
    // 修改发送消息的方法，增强错误处理
    public boolean sendMessage(String topic, String message) {
    // 重试计数器
    int retryCount = 0;
    final int MAX_RETRIES = 3;
    
    while (retryCount < MAX_RETRIES) {
    // 检查通道和插件ID
    if (blockingStub == null || pluginId == null || pluginId.isEmpty()) {
    logger.warn("通道或插件ID无效，尝试重新初始化 (重试 {}/{})", retryCount + 1, MAX_RETRIES);
    
    // 重新初始化
    initGrpcChannel();
    
    // 如果仍然无效，增加重试计数
    if (blockingStub == null || pluginId == null || pluginId.isEmpty()) {
    retryCount++;
    try {
    // 等待一段时间再重试
    Thread.sleep(1000 * retryCount);
    } catch (InterruptedException e) {
    Thread.currentThread().interrupt();
    }
    continue;
    }
    }
    
    try {
    logger.info("发送消息到主题: {} (重试 {}/{})", topic, retryCount, MAX_RETRIES);
    
    // 直接使用MqttService发布消息，绕过gRPC调用
    boolean success = mqttService.publish(topic, message);
    
    if (success) {
    logger.info("消息发送成功到主题: {}", topic);
    return true;
    } else {
    logger.error("消息发送失败到主题: {}", topic);
    retryCount++;
    }
    } catch (Exception e) {
    logger.error("发送消息时发生错误 (重试 {}/{}): {}", retryCount, MAX_RETRIES, e.getMessage());
    
    // 增加重试计数
    retryCount++;
    
    try {
    // 等待一段时间再重试
    Thread.sleep(2000 * retryCount);
    } catch (InterruptedException ie) {
    Thread.currentThread().interrupt();
    }
    }
    }
    
    logger.error("发送消息失败，已达到最大重试次数: {}", MAX_RETRIES);
    return false;
    }
}