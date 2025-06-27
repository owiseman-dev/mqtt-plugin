'use client';

import { useState } from 'react';
import mqtt, { MqttClient } from 'mqtt';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Wifi, WifiOff } from 'lucide-react';

interface MqttConnectionProps {
  onMessage?: (topic: string, message: string) => void;
  onConnectionChange?: (client: MqttClient | null) => void;
}



export default function MqttConnection({ onMessage, onConnectionChange }: MqttConnectionProps) {
  const [client, setClient] = useState<MqttClient | null>(null);
  const [connected, setConnected] = useState(false);
  const [connecting, setConnecting] = useState(false);
  const [brokerUrl, setBrokerUrl] = useState('ws://localhost:8083/mqtt');
  const [subscriptions, setSubscriptions] = useState<string[]>(['devices/+/data', 'devices/+/status']);
  const [newTopic, setNewTopic] = useState('');

  const handleConnect = async () => {
    if (connected) {
      // 断开连接
      if (client) {
        client.end();
        setClient(null);
        setConnected(false);
        onConnectionChange?.(null);
      }
      return;
    }

    setConnecting(true);
    try {
      const mqttClient = mqtt.connect(brokerUrl, {
        clientId: `iot-dashboard-${Math.random().toString(16).substr(2, 8)}`,
        clean: true,
        connectTimeout: 4000,
        username: '',
        password: '',
        reconnectPeriod: 1000,
      });

      mqttClient.on('connect', () => {
        console.log('MQTT连接成功');
        setConnected(true);
        setConnecting(false);
        setClient(mqttClient);
        onConnectionChange?.(mqttClient);
        
        // 订阅默认主题
        subscriptions.forEach(topic => {
          mqttClient.subscribe(topic, (err) => {
            if (!err) {
              console.log(`Subscribed to ${topic}`);
            }
          });
        });
      });

      mqttClient.on('error', (error: Error) => {
        console.error('MQTT连接错误:', error);
        setConnecting(false);
        setConnected(false);
      });

      mqttClient.on('message', (topic: string, message: Buffer) => {
        const messageStr = message.toString();
        console.log(`收到消息 [${topic}]: ${messageStr}`);
        
        onMessage?.(topic, messageStr);
      });

      mqttClient.on('close', () => {
        console.log('MQTT连接关闭');
        setConnected(false);
        setClient(null);
        onConnectionChange?.(null);
      });

    } catch (error) {
      console.error('连接失败:', error);
      setConnecting(false);
    }
  };

  const disconnect = () => {
    if (client) {
      client.end();
    }
  };

  const addSubscription = () => {
    if (newTopic && client && connected) {
      client.subscribe(newTopic, (err: any) => {
        if (!err) {
          setSubscriptions(prev => [...prev, newTopic]);
          setNewTopic('');
        }
      });
    }
  };

  const removeSubscription = (topic: string) => {
    if (client && connected) {
      client.unsubscribe(topic);
      setSubscriptions(prev => prev.filter(t => t !== topic));
    }
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 mb-4">
        {connected ? (
          <Wifi className="h-5 w-5 text-green-500" />
        ) : (
          <WifiOff className="h-5 w-5 text-red-500" />
        )}
        <Badge variant={connected ? "default" : "destructive"}>
          {connected ? '已连接' : '未连接'}
        </Badge>
      </div>

      <div className="space-y-2">
        <Label htmlFor="broker-url">MQTT Broker URL</Label>
        <Input
          id="broker-url"
          value={brokerUrl}
          onChange={(e) => setBrokerUrl(e.target.value)}
          placeholder="ws://localhost:8083/mqtt"
          disabled={connected}
        />
      </div>

      <div className="flex gap-2">
        <Button 
          onClick={handleConnect} 
          disabled={connecting}
          className="flex-1"
        >
          {connecting ? '连接中...' : '连接'}
        </Button>
        <Button 
          onClick={disconnect} 
          disabled={!connected}
          variant="outline"
          className="flex-1"
        >
          断开连接
        </Button>
      </div>

      {connected && (
        <div className="space-y-3">
          <Label>订阅主题</Label>
          <div className="flex gap-2">
            <Input
              value={newTopic}
              onChange={(e) => setNewTopic(e.target.value)}
              placeholder="输入主题名称"
              onKeyPress={(e) => e.key === 'Enter' && addSubscription()}
            />
            <Button onClick={addSubscription} size="sm">
              订阅
            </Button>
          </div>
          
          <div className="space-y-1">
            {subscriptions.map((topic, index) => (
              <div key={index} className="flex items-center justify-between bg-gray-50 p-2 rounded">
                <span className="text-sm font-mono">{topic}</span>
                <Button
                  onClick={() => removeSubscription(topic)}
                  size="sm"
                  variant="ghost"
                  className="h-6 w-6 p-0"
                >
                  ×
                </Button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}