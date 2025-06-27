'use client';

import { useMemo } from 'react';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
  AreaChart,
  Area,
  BarChart,
  Bar
} from 'recharts';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { TrendingUp, BarChart3, Activity } from 'lucide-react';

interface SensorData {
  timestamp: string;
  deviceId: string;
  value: number;
  type: string;
}

interface DataVisualizationProps {
  data: SensorData[];
}

export default function DataVisualization({ data }: DataVisualizationProps) {
  const processedData = useMemo(() => {
    if (!data || data.length === 0) return [];
    
    // 按时间戳分组数据
    const groupedByTime = data.reduce((acc, item) => {
      const time = new Date(item.timestamp).toLocaleTimeString();
      if (!acc[time]) {
        acc[time] = { time };
      }
      acc[time][`${item.deviceId}_${item.type}`] = item.value;
      return acc;
    }, {} as Record<string, Record<string, number | string>>);
    
    return Object.values(groupedByTime);
  }, [data]);

  const deviceTypes = useMemo(() => {
    const types = new Set(data.map(d => `${d.deviceId}_${d.type}`));
    return Array.from(types);
  }, [data]);

  const getRandomColor = (index: number) => {
    const colors = [
      '#8884d8', '#82ca9d', '#ffc658', '#ff7300', 
      '#00ff00', '#ff00ff', '#00ffff', '#ff0000'
    ];
    return colors[index % colors.length];
  };

  const latestValues = useMemo(() => {
    const latest = data.reduce((acc, item) => {
      const key = `${item.deviceId}_${item.type}`;
      if (!acc[key] || new Date(item.timestamp) > new Date(acc[key].timestamp)) {
        acc[key] = item;
      }
      return acc;
    }, {} as Record<string, SensorData>);
    
    return Object.values(latest);
  }, [data]);

  const statisticsData = useMemo(() => {
    return deviceTypes.map(deviceType => {
      const deviceData = data.filter(d => `${d.deviceId}_${d.type}` === deviceType);
      const values = deviceData.map(d => d.value);
      
      return {
        device: deviceType,
        min: Math.min(...values),
        max: Math.max(...values),
        avg: values.reduce((a, b) => a + b, 0) / values.length,
        count: values.length
      };
    });
  }, [data, deviceTypes]);

  if (!data || data.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        <Activity className="h-16 w-16 mx-auto mb-4 opacity-50" />
        <h3 className="text-lg font-medium mb-2">暂无数据</h3>
        <p>等待设备发送传感器数据...</p>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* 实时数值卡片 */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {latestValues.map((item, index) => (
          <Card key={`${item.deviceId}_${item.type}`}>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm font-medium text-gray-600">
                {item.deviceId} - {item.type}
              </CardTitle>
            </CardHeader>
            <CardContent>
              <div className="text-2xl font-bold" style={{ color: getRandomColor(index) }}>
                {item.value.toFixed(2)}
              </div>
              <div className="text-xs text-gray-500 mt-1">
                {new Date(item.timestamp).toLocaleString()}
              </div>
            </CardContent>
          </Card>
        ))}
      </div>

      <Tabs defaultValue="line" className="w-full">
        <TabsList className="grid w-full grid-cols-3">
          <TabsTrigger value="line" className="flex items-center gap-2">
            <TrendingUp className="h-4 w-4" />
            趋势图
          </TabsTrigger>
          <TabsTrigger value="area" className="flex items-center gap-2">
            <Activity className="h-4 w-4" />
            面积图
          </TabsTrigger>
          <TabsTrigger value="stats" className="flex items-center gap-2">
            <BarChart3 className="h-4 w-4" />
            统计
          </TabsTrigger>
        </TabsList>

        <TabsContent value="line" className="mt-6">
          <Card>
            <CardHeader>
              <CardTitle>实时数据趋势</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={400}>
                <LineChart data={processedData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  {deviceTypes.map((deviceType, index) => (
                    <Line
                      key={deviceType}
                      type="monotone"
                      dataKey={deviceType}
                      stroke={getRandomColor(index)}
                      strokeWidth={2}
                      dot={{ r: 4 }}
                      connectNulls={false}
                    />
                  ))}
                </LineChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="area" className="mt-6">
          <Card>
            <CardHeader>
              <CardTitle>数据分布面积图</CardTitle>
            </CardHeader>
            <CardContent>
              <ResponsiveContainer width="100%" height={400}>
                <AreaChart data={processedData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis dataKey="time" />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  {deviceTypes.map((deviceType, index) => (
                    <Area
                      key={deviceType}
                      type="monotone"
                      dataKey={deviceType}
                      stackId="1"
                      stroke={getRandomColor(index)}
                      fill={getRandomColor(index)}
                      fillOpacity={0.6}
                    />
                  ))}
                </AreaChart>
              </ResponsiveContainer>
            </CardContent>
          </Card>
        </TabsContent>

        <TabsContent value="stats" className="mt-6">
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            <Card>
              <CardHeader>
                <CardTitle>设备统计</CardTitle>
              </CardHeader>
              <CardContent>
                <ResponsiveContainer width="100%" height={300}>
                  <BarChart data={statisticsData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="device" angle={-45} textAnchor="end" height={80} />
                    <YAxis />
                    <Tooltip />
                    <Legend />
                    <Bar dataKey="min" fill="#ff7300" name="最小值" />
                    <Bar dataKey="avg" fill="#8884d8" name="平均值" />
                    <Bar dataKey="max" fill="#82ca9d" name="最大值" />
                  </BarChart>
                </ResponsiveContainer>
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>数据统计表</CardTitle>
              </CardHeader>
              <CardContent>
                <div className="overflow-x-auto">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="border-b">
                        <th className="text-left p-2">设备</th>
                        <th className="text-right p-2">最小值</th>
                        <th className="text-right p-2">最大值</th>
                        <th className="text-right p-2">平均值</th>
                        <th className="text-right p-2">数据点</th>
                      </tr>
                    </thead>
                    <tbody>
                      {statisticsData.map((stat) => (
                        <tr key={stat.device} className="border-b">
                          <td className="p-2 font-medium">{stat.device}</td>
                          <td className="p-2 text-right">{stat.min.toFixed(2)}</td>
                          <td className="p-2 text-right">{stat.max.toFixed(2)}</td>
                          <td className="p-2 text-right">{stat.avg.toFixed(2)}</td>
                          <td className="p-2 text-right">{stat.count}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          </div>
        </TabsContent>
      </Tabs>
    </div>
  );
}