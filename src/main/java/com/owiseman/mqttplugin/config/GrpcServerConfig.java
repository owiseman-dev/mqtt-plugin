package com.owiseman.mqttplugin.config;

import com.owiseman.mqttplugin.grpc.MqttPluginGrpcService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PreDestroy;
import java.io.IOException;

@Configuration
public class GrpcServerConfig {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerConfig.class);

    @Value("${plugin.grpc.port:8085}")
    private int grpcPort;

    @Autowired
    private MqttPluginGrpcService mqttPluginGrpcService;

    private Server server;

    @Bean
    public Server grpcServer() throws IOException {
        logger.info("启动MQTT插件gRPC服务器，监听端口 {}", grpcPort);
        
        server = ServerBuilder.forPort(grpcPort)
                .addService(mqttPluginGrpcService)
                .build()
                .start();
        
        logger.info("MQTT插件gRPC服务器已启动，监听端口 {}", grpcPort);
        
        // 在一个新线程中等待终止
        new Thread(() -> {
            try {
                server.awaitTermination();
            } catch (InterruptedException e) {
                logger.error("gRPC服务器被中断", e);
            }
        }).start();
        
        return server;
    }

    @PreDestroy
    public void stopServer() {
        if (server != null) {
            logger.info("关闭gRPC服务器...");
            server.shutdown();
            try {
                if (!server.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    server.shutdownNow();
                }
            } catch (InterruptedException e) {
                server.shutdownNow();
            }
            logger.info("gRPC服务器已关闭");
        }
    }
}