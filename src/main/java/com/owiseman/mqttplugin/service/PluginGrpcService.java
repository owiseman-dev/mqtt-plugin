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

    @Scheduled(fixedRate = 30000) // 每30秒发送一次心跳
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

    // 修改初始化方法，增加更多连接参数
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
            
            // 创建新通道，增加更多连接参数
            channel = ManagedChannelBuilder.forAddress(mqttConfig.getDataApiHost(), mqttConfig.getDataApiPort())
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .keepAliveWithoutCalls(true)
                    .maxInboundMessageSize(10 * 1024 * 1024)  // 10MB
                    .enableRetry()  // 启用重试
                    .maxRetryAttempts(5)  // 最大重试次数
                    .build();
                
            blockingStub = PluginServiceGrpc.newBlockingStub(channel)
                    .withWaitForReady()  // 等待就绪
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
            
            GetPluginByNameResponse findResponse = blockingStub.getPluginByName(findRequest);
            
            if (findResponse.getFound()) {
                // 如果找到了插件，使用已有的ID
                String existingPluginId = findResponse.getPlugin().getPluginId();
                logger.info("插件已存在，使用现有ID: {}", existingPluginId);
                
                // 更新插件状态和连接信息
                UpdatePluginRequest updateRequest = UpdatePluginRequest.newBuilder()
                        .setPluginId(existingPluginId)
                        .setStatus("REGISTERED")
                        .setHost(mqttConfig.getHost())
                        .setPort(mqttConfig.getPluginPort())
                        .build();
                
                UpdatePluginResponse updateResponse = blockingStub.updatePlugin(updateRequest);
                
                if (updateResponse.getSuccess()) {
                    this.pluginId = existingPluginId;
                    logger.info("插件信息更新成功: {}", updateResponse.getMessage());
                } else {
                    logger.error("插件信息更新失败: {}", updateResponse.getMessage());
                }
            } else {
                // 如果没找到，则注册新插件
                PluginRegistration registration = PluginRegistration.newBuilder()
                        .setName(pluginName)
                        .setVersion(mqttConfig.getPluginVersion())
                        .setType(mqttConfig.getPluginType())
                        .setDescription(mqttConfig.getPluginDescription())
                        .setHost(mqttConfig.getHost())
                        .setPort(mqttConfig.getPluginPort())
                        .build();
                
                RegistrationResponse response = blockingStub.registerPlugin(registration);
                
                if (response.getSuccess()) {
                    this.pluginId = response.getPluginId();
                    logger.info("插件注册成功，ID: {}", pluginId);
                } else {
                    logger.error("插件注册失败: {}", response.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("注册插件时发生错误", e);
            // 不设置pluginId，让下次心跳时重试
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
    public boolean sendMessage(String topic, String message) {
        if (pluginId == null || blockingStub == null) {
            logger.warn("无法发送消息，插件未注册或gRPC通道未初始化");
            
            // 尝试初始化和注册
            if (blockingStub == null) {
                initGrpcChannel();
            }
            
            if (pluginId == null && blockingStub != null) {
                registerPlugin();
            }
            
            // 如果仍然无法初始化，则返回失败
            if (pluginId == null || blockingStub == null) {
                return false;
            }
        }
        
        try {
            logger.info("发送消息到主题: {}", topic);
            
            // 构建命令请求
            CommandRequest request = CommandRequest.newBuilder()
                    .setPluginId(pluginId)
                    .setCommand("publish")
                    .putParameters("topic", topic)
                    .putParameters("message", message)
                    .build();
            
            // 设置超时时间
            CommandResponse response = blockingStub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .executeCommand(request);
            
            if (response.getSuccess()) {
                logger.info("消息发送成功: {}", response.getResult());
                return true;
            } else {
                logger.error("消息发送失败: {}", response.getErrorMessage());
                return false;
            }
        } catch (Exception e) {
            logger.error("发送消息时发生错误: {}", e.getMessage());
            
            // 如果是网络问题，尝试重新连接
            if (e.getMessage().contains("UNAVAILABLE") || 
                e.getMessage().contains("Network closed") || 
                e.getMessage().contains("Connection")) {
                
                logger.info("检测到网络问题，尝试重新初始化gRPC通道");
                initGrpcChannel();
                
                // 重试一次
                try {
                    if (blockingStub != null) {
                        logger.info("重试发送消息到主题: {}", topic);
                        
                        CommandRequest request = CommandRequest.newBuilder()
                                .setPluginId(pluginId)
                                .setCommand("publish")
                                .putParameters("topic", topic)
                                .putParameters("message", message)
                                .build();
                        
                        CommandResponse response = blockingStub
                                .withDeadlineAfter(10, TimeUnit.SECONDS)
                                .executeCommand(request);
                        
                        if (response.getSuccess()) {
                            logger.info("重试消息发送成功: {}", response.getResult());
                            return true;
                        } else {
                            logger.error("重试消息发送失败: {}", response.getErrorMessage());
                        }
                    }
                } catch (Exception retryEx) {
                    logger.error("重试发送消息时发生错误: {}", retryEx.getMessage());
                }
            }
            
            return false;
        }
    }
}