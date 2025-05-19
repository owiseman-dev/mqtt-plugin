# MQTT Plugin

这是一个基于Moquette的MQTT服务插件，可以集成到数据API项目中。

## 功能特性

- 提供完整的MQTT代理服务
- 支持MQTT 3.1.1协议
- 支持WebSocket连接
- 与数据API项目集成，作为插件运行
- 提供REST API进行管理

## 配置说明

配置文件位于`src/main/resources/application.properties`，主要配置项包括：

- MQTT服务器配置（端口、WebSocket端口等）
- 插件信息配置（名称、版本等）
- 主应用gRPC服务器配置

## 使用方法

### 启动服务

```bash
mvn spring-boot:run

### 新增一些特性
1. 提供更多的设备管理方式
2. red-node的接入