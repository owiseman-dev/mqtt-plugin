'use client';

import { useState } from 'react';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import MqttConnection from '@/components/MqttConnection';
import DeviceList from '@/components/DeviceList';
import DataVisualization from '@/components/DataVisualization';
import NodeRedEditor from '@/components/NodeRedEditor';
import { Activity, Wifi, BarChart3, Workflow } from 'lucide-react';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';

interface MqttMessage {
  topic: string;
  message: string;
  timestamp: string;
}

interface Device {
  id: string;
  name: string;
  status: 'online' | 'offline';
  lastSeen: string;
  sensors: Record<string, number>;
}

interface SensorData {
  timestamp: string;
  deviceId: string;
  value: number;
  type: string;
}

interface NodeRedFlow {
  nodes: number;
  timestamp: string;
}

export default function Home() {
  const [mqttMessages, setMqttMessages] = useState<MqttMessage[]>([]);
  const [mqttClient, setMqttClient] = useState<any>(null);
  const [devices, setDevices] = useState<Device[]>([]);
  const [sensorData, setSensorData] = useState<SensorData[]>([]);

  const [nodeRedFlow, setNodeRedFlow] = useState<NodeRedFlow | null>(null);

  const handleDeviceUpdate = (deviceData: any) => {
    setDevices(prev => {
      const existingIndex = prev.findIndex(d => d.id === deviceData.id);
      if (existingIndex >= 0) {
        const updated = [...prev];
        updated[existingIndex] = { ...updated[existingIndex], ...deviceData };
        return updated;
      }
      return [...prev, deviceData];
    });

    // 添加到传感器数据用于可视化
    if (deviceData.sensorValue !== undefined) {
      setSensorData(prev => [
        ...prev.slice(-49), // 保持最近50个数据点
        {
          timestamp: new Date().toISOString(),
          deviceId: deviceData.id,
          value: deviceData.sensorValue,
          type: deviceData.type || 'unknown'
        }
      ]);
    }
  };

  return (
    <div className="min-h-screen bg-gray-50 p-4">
      <div className="max-w-7xl mx-auto">
        <header className="mb-8">
          <h1 className="text-4xl font-bold text-gray-900 mb-2">
            物联网数据可视化平台
          </h1>
          <p className="text-gray-600">
            基于 MQTT 的实时设备监控与控制系统
          </p>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <Card>
            <CardHeader>
              <CardTitle>MQTT 连接</CardTitle>
              <CardDescription>配置和管理 MQTT 连接</CardDescription>
            </CardHeader>
            <CardContent>
              <MqttConnection
                onClientChange={setMqttClient}
                onDeviceUpdate={handleDeviceUpdate}
              />
            </CardContent>
          </Card>

          <Card className="lg:col-span-2">
            <CardHeader>
              <CardTitle>设备状态</CardTitle>
              <CardDescription>实时设备列表和状态监控</CardDescription>
            </CardHeader>
            <CardContent>
              <DeviceList devices={devices} mqttClient={mqttClient} />
            </CardContent>
          </Card>
        </div>

        <Tabs defaultValue="visualization" className="w-full">
          <TabsList className="grid w-full grid-cols-2">
            <TabsTrigger value="visualization">数据可视化</TabsTrigger>
            <TabsTrigger value="node-red">Node-RED 编辑器</TabsTrigger>
          </TabsList>

          <TabsContent value="visualization" className="mt-6">
            <Card>
              <CardHeader>
                <CardTitle>实时数据图表</CardTitle>
                <CardDescription>传感器数据的实时可视化</CardDescription>
              </CardHeader>
              <CardContent>
                <DataVisualization data={sensorData} />
              </CardContent>
            </Card>
          </TabsContent>

          <TabsContent value="node-red" className="mt-6">
            <Card>
              <CardHeader>
                <CardTitle>Node-RED 流程编辑器</CardTitle>
                <CardDescription>可视化编程和自动化流程</CardDescription>
              </CardHeader>
              <CardContent>
                <NodeRedEditor />
              </CardContent>
            </Card>
          </TabsContent>
        </Tabs>
      </div>
    </div>
  );
}
