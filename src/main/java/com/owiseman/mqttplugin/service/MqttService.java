package com.owiseman.mqttplugin.service;

import com.owiseman.mqttplugin.config.MqttConfig;
import io.moquette.broker.Server;
import io.moquette.broker.config.MemoryConfig;
import io.moquette.interception.InterceptHandler;
import io.moquette.interception.messages.*;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttQoS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;

@Service
public class MqttService {

    private static final Logger logger = LoggerFactory.getLogger(MqttService.class);

    private final MqttConfig mqttConfig;
    private final Server mqttBroker;
    private boolean running = false;
    private long startTime;

    @Autowired
    public MqttService(MqttConfig mqttConfig) {
        this.mqttConfig = mqttConfig;
        this.mqttBroker = new Server();
    }

    public void start() throws Exception {
        if (running) {
            return;
        }

        logger.info("Starting MQTT broker on port {} and websocket port {}", mqttConfig.getPort(), mqttConfig.getWebsocketPort());

        // 配置MQTT服务器
        Properties properties = new Properties();
        properties.setProperty("host", mqttConfig.getHost());
        properties.setProperty("port", String.valueOf(mqttConfig.getPort()));
        properties.setProperty("websocket_port", String.valueOf(mqttConfig.getWebsocketPort()));
        properties.setProperty("allow_anonymous", String.valueOf(mqttConfig.isAllowAnonymous()));
        properties.setProperty("netty.epoll", String.valueOf(mqttConfig.isNettyEpoll()));

        // 添加消息拦截器
        InterceptHandler interceptHandler = new InterceptHandler() {
            @Override
            public void onPublish(InterceptPublishMessage message) {
                logger.debug("Publish received: {} on topic {}",
                        new String(message.getPayload().array(), StandardCharsets.UTF_8),
                        message.getTopicName());
            }

            @Override
            public String getID() {
                return "";
            }

            @Override
            public Class<?>[] getInterceptedMessageTypes() {
                return new Class[0];
            }

            @Override
            public void onConnect(InterceptConnectMessage message) {
                logger.info("Client connected: {}", message.getClientID());
            }

            @Override
            public void onDisconnect(InterceptDisconnectMessage message) {
                logger.info("Client disconnected: {}", message.getClientID());
            }

            @Override
            public void onConnectionLost(InterceptConnectionLostMessage message) {
                logger.info("Connection lost for client: {}", message.getClientID());
            }

            @Override
            public void onSubscribe(InterceptSubscribeMessage message) {
                logger.info("Subscription from client {}: {}", message.getClientID(), message.getTopicFilter());
            }

            @Override
            public void onUnsubscribe(InterceptUnsubscribeMessage message) {
                logger.info("Unsubscription from client {}: {}", message.getClientID(), message.getTopicFilter());
            }

            @Override
            public void onMessageAcknowledged(InterceptAcknowledgedMessage interceptAcknowledgedMessage) {

            }
        };

        // 启动MQTT服务器
        MemoryConfig config = new MemoryConfig(properties);
        mqttBroker.startServer(config, Collections.singletonList(interceptHandler));

        running = true;
        startTime = System.currentTimeMillis();
        logger.info("MQTT broker started successfully");
    }

    public void stop() throws Exception {
        if (!running) {
            return;
        }

        logger.info("Stopping MQTT broker");
        mqttBroker.stopServer();
        running = false;
        logger.info("MQTT broker stopped");
    }

    public boolean isRunning() {
        return running;
    }

    // 添加 getUptime 方法（如果不存在）
    public long getUptime() {
        // 如果服务未运行，返回0
        if (!isRunning()) {
            return 0;
        }
        return System.currentTimeMillis() - startTime;
    }

    public void publishMessage(String topic, String message, int qos) {
        if (!running) {
            logger.warn("Cannot publish message, MQTT broker is not running");
            return;
        }

        mqttBroker.internalPublish(
                MqttMessageBuilders.publish()
                        .topicName(topic)
                        .retained(false)
                        .qos(MqttQoS.valueOf(qos))
                        .payload(Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8)))
                        .build(),
                "INTERNAL"
        );

        logger.debug("Published message to topic {}: {}", topic, message);
    }
    
    // 修改publish方法，使用mqttBroker而不是不存在的mqttClient
    public boolean publish(String topic, String message) {
        try {
            logger.info("发布消息到主题: {}", topic);
            
            // 检查MQTT服务器是否运行
            if (!running) {
                logger.warn("MQTT服务器未运行，尝试启动");
                try {
                    start();
                } catch (Exception e) {
                    logger.error("启动MQTT服务器失败: {}", e.getMessage());
                    return false;
                }
            }
            
            // 使用内部发布方法
            mqttBroker.internalPublish(
                    MqttMessageBuilders.publish()
                            .topicName(topic)
                            .retained(false)
                            .qos(MqttQoS.valueOf(1))  // QoS 1
                            .payload(Unpooled.copiedBuffer(message.getBytes(StandardCharsets.UTF_8)))
                            .build(),
                    "INTERNAL"
            );
            
            logger.info("消息已成功发布到主题: {}", topic);
            return true;
        } catch (Exception e) {
            logger.error("发布消息时发生错误: {}", e.getMessage());
            return false;
        }
    }
}