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

    public long getUptime() {
        if (!running) {
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
}