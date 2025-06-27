'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Power, Thermometer, Droplets, Zap, Send } from 'lucide-react';

interface Device {
  id: string;
  type: string;
  lastSeen: string;
  status?: string;
  sensorValue?: number;
  unit?: string;
  online?: boolean;
}

interface DeviceListProps {
  devices: Device[];
  onSendCommand?: (deviceId: string, command: CommandMessage) => void;
}

interface CommandMessage {
  deviceId: string;
  command: string;
  value?: string | number;
  timestamp: string;
}

export default function DeviceList({ devices, onSendCommand }: DeviceListProps) {
  const [customMessage, setCustomMessage] = useState('');
  const [selectedDevice, setSelectedDevice] = useState('');

  const sendCommand = (deviceId: string, command: string, value?: string | number) => {
    const commandData: CommandMessage = {
      deviceId,
      command,
      value,
      timestamp: new Date().toISOString()
    };
    
    console.log('发送命令:', commandData);
    onSendCommand?.(deviceId, commandData);
  };

  const sendCustomMessage = () => {
    if (!selectedDevice || !customMessage.trim()) return;
    
    try {
      const messageData = JSON.parse(customMessage);
      sendCommand(selectedDevice, 'custom', JSON.stringify(messageData));
      setCustomMessage('');
    } catch {
      // 发送原始字符串
      sendCommand(selectedDevice, 'message', customMessage);
      setCustomMessage('');
    }
  };

  const getDeviceIcon = (type: string) => {
    switch (type.toLowerCase()) {
      case 'temperature':
      case 'temp':
        return <Thermometer className="h-4 w-4" />;
      case 'humidity':
        return <Droplets className="h-4 w-4" />;
      case 'power':
      case 'switch':
        return <Zap className="h-4 w-4" />;
      default:
        return <Power className="h-4 w-4" />;
    }
  };

  const formatLastSeen = (timestamp: string) => {
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    
    if (minutes < 1) return '刚刚';
    if (minutes < 60) return `${minutes}分钟前`;
    if (minutes < 1440) return `${Math.floor(minutes / 60)}小时前`;
    return date.toLocaleDateString();
  };

  const isDeviceOnline = (lastSeen: string) => {
    const diff = new Date().getTime() - new Date(lastSeen).getTime();
    return diff < 300000; // 5分钟内认为在线
  };

  if (devices.length === 0) {
    return (
      <div className="text-center py-8 text-gray-500">
        <Power className="h-12 w-12 mx-auto mb-4 opacity-50" />
        <p>暂无设备数据</p>
        <p className="text-sm">请确保设备已连接并发送数据到 MQTT</p>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {devices.map((device) => {
          const online = isDeviceOnline(device.lastSeen);
          
          return (
            <Card key={device.id} className={`transition-all hover:shadow-md ${
              online ? 'border-green-200' : 'border-gray-200'
            }`}>
              <CardHeader className="pb-3">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    {getDeviceIcon(device.type)}
                    <CardTitle className="text-lg">{device.id}</CardTitle>
                  </div>
                  <Badge variant={online ? "default" : "secondary"}>
                    {online ? '在线' : '离线'}
                  </Badge>
                </div>
              </CardHeader>
              
              <CardContent className="space-y-3">
                <div className="grid grid-cols-2 gap-2 text-sm">
                  <div>
                    <span className="text-gray-500">类型:</span>
                    <p className="font-medium">{device.type}</p>
                  </div>
                  <div>
                    <span className="text-gray-500">最后活跃:</span>
                    <p className="font-medium">{formatLastSeen(device.lastSeen)}</p>
                  </div>
                </div>
                
                {device.sensorValue !== undefined && (
                  <div className="bg-blue-50 p-3 rounded-lg">
                    <div className="text-sm text-gray-600">传感器数值</div>
                    <div className="text-2xl font-bold text-blue-600">
                      {device.sensorValue}
                      {device.unit && <span className="text-sm ml-1">{device.unit}</span>}
                    </div>
                  </div>
                )}
                
                {device.status && (
                  <div className="flex items-center gap-2">
                    <span className="text-sm text-gray-500">状态:</span>
                    <Badge variant={device.status === 'on' ? 'default' : 'secondary'}>
                      {device.status}
                    </Badge>
                  </div>
                )}
                
                <div className="flex gap-2 pt-2">
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => sendControlCommand(device.id, 'toggle')}
                    disabled={!mqttClient || !online}
                    className="flex-1"
                  >
                    <Power className="h-3 w-3 mr-1" />
                    切换
                  </Button>
                  
                  <Button
                    size="sm"
                    variant="outline"
                    onClick={() => setSelectedDevice(device.id)}
                    disabled={!mqttClient}
                  >
                    <Send className="h-3 w-3" />
                  </Button>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>
      
      {selectedDevice && (
        <Card className="mt-6">
          <CardHeader>
            <CardTitle>发送自定义命令到 {selectedDevice}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-4">
            <div>
              <label className="text-sm font-medium">JSON 消息:</label>
              <Input
                value={controlMessage}
                onChange={(e) => setControlMessage(e.target.value)}
                placeholder='{"command": "setValue", "value": 25}'
                className="font-mono"
              />
            </div>
            <div className="flex gap-2">
              <Button
                onClick={() => sendCustomMessage(selectedDevice)}
                disabled={!controlMessage.trim()}
              >
                发送
              </Button>
              <Button
                variant="outline"
                onClick={() => {
                  setSelectedDevice(null);
                  setControlMessage('');
                }}
              >
                取消
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  );
}