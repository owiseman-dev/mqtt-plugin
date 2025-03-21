package com.owiseman.mqttplugin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqttConfig {

    @Value("${mqtt.host}")
    private String host;

    @Value("${mqtt.port}")
    private int port;

    @Value("${mqtt.websocket.port}")
    private int websocketPort;

    @Value("${mqtt.allow.anonymous}")
    private boolean allowAnonymous;

    @Value("${mqtt.netty.epoll}")
    private boolean nettyEpoll;

    @Value("${plugin.name}")
    private String pluginName;

    @Value("${plugin.version}")
    private String pluginVersion;

    @Value("${plugin.type}")
    private String pluginType;

    @Value("${plugin.description}")
    private String pluginDescription;

    @Value("${plugin.host}")
    private String pluginHost;

    @Value("${plugin.port}")
    private int pluginPort;

    @Value("${dataapi.grpc.host}")
    private String dataApiHost;

    @Value("${dataapi.grpc.port}")
    private int dataApiPort;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getWebsocketPort() {
        return websocketPort;
    }

    public boolean isAllowAnonymous() {
        return allowAnonymous;
    }

    public boolean isNettyEpoll() {
        return nettyEpoll;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String getPluginVersion() {
        return pluginVersion;
    }

    public String getPluginType() {
        return pluginType;
    }

    public String getPluginDescription() {
        return pluginDescription;
    }

    public String getPluginHost() {
        return pluginHost;
    }

    public int getPluginPort() {
        return pluginPort;
    }

    public String getDataApiHost() {
        return dataApiHost;
    }

    public int getDataApiPort() {
        return dataApiPort;
    }
}