package com.owiseman.mqttplugin;

import com.owiseman.mqttplugin.service.MqttService;
import com.owiseman.mqttplugin.service.PluginGrpcService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MqttPluginApplication implements CommandLineRunner {

    @Autowired
    private MqttService mqttService;

    @Autowired
    private PluginGrpcService pluginGrpcService;

    public static void main(String[] args) {
        SpringApplication.run(MqttPluginApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // 启动MQTT服务
        mqttService.start();

        // 注册插件到主应用
        pluginGrpcService.registerPlugin();

        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                mqttService.stop();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }));
    }
}