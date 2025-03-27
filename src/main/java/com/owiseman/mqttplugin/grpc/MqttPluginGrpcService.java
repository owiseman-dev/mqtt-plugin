package com.owiseman.mqttplugin.grpc;

import com.owiseman.dataapi.proto.*;
import com.owiseman.mqttplugin.service.MqttService;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.FileDescriptor;

@Service
public class MqttPluginGrpcService extends PluginServiceGrpc.PluginServiceImplBase {

    private static final Logger logger = LoggerFactory.getLogger(MqttPluginGrpcService.class);

    @Autowired
    private MqttService mqttService;

    @Override
    public void executeCommand(CommandRequest request, StreamObserver<CommandResponse> responseObserver) {
        String command = request.getCommand();
        logger.info("收到命令: {}", command);
        
        try {
            if ("publish".equals(command)) {
                String topic = request.getParametersMap().getOrDefault("topic", "");
                String message = request.getParametersMap().getOrDefault("message", "");
                
                if (topic.isEmpty()) {
                    throw new IllegalArgumentException("主题不能为空");
                }
                
                // 发布MQTT消息
                boolean success = mqttService.publish(topic, message);
                
                if (success) {
                    CommandResponse response = CommandResponse.newBuilder()
                            .setSuccess(true)
                            .setResult("消息已发布到主题: " + topic)
                            .build();
                    
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } else {
                    throw new RuntimeException("发布消息失败");
                }
            } else if ("start".equals(command)) {
                // 处理启动命令
                try {
                    mqttService.start();
                    CommandResponse response = CommandResponse.newBuilder()
                            .setSuccess(true)
                            .setResult("MQTT服务已成功启动")
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    throw new RuntimeException("启动MQTT服务失败: " + e.getMessage());
                }
            } else if ("stop".equals(command)) {
                // 处理停止命令
                try {
                    mqttService.stop();
                    CommandResponse response = CommandResponse.newBuilder()
                            .setSuccess(true)
                            .setResult("MQTT服务已成功停止")
                            .build();
                    responseObserver.onNext(response);
                    responseObserver.onCompleted();
                } catch (Exception e) {
                    throw new RuntimeException("停止MQTT服务失败: " + e.getMessage());
                }
            } else {
                CommandResponse response = CommandResponse.newBuilder()
                        .setSuccess(false)
                        .setErrorMessage("不支持的命令: " + command)
                        .build();
                
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        } catch (Exception e) {
            logger.error("执行命令时发生错误: {}", e.getMessage());
            
            CommandResponse response = CommandResponse.newBuilder()
                    .setSuccess(false)
                    .setErrorMessage("错误: " + e.getMessage())
                    .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
    
    // 添加心跳方法
    @Override
    public void heartbeat(HeartbeatRequest request, StreamObserver<HeartbeatResponse> responseObserver) {
        logger.debug("收到心跳请求");
        
        HeartbeatResponse response = HeartbeatResponse.newBuilder()
                .setReceived(true)
                .setServerTime(System.currentTimeMillis())
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
        logger.debug("已响应心跳请求");
    }
    
    // 修改状态查询方法，使用正确的请求和响应类型
    @Override
    public void getStatus(StatusRequest request, StreamObserver<StatusResponse> responseObserver) {
        logger.debug("收到状态请求，插件ID: {}", request.getPluginId());
        
        StatusResponse response = StatusResponse.newBuilder()
                .setStatus(mqttService.isRunning() ? "RUNNING" : "STOPPED")
                .setUptime(mqttService.getUptime())

                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
        logger.debug("已响应状态请求");
    }
    
    // 删除不存在的startPlugin和stopPlugin方法，因为已经在executeCommand中实现了相应功能
}