#!/usr/bin/env node

const readline = require('readline');

// Base API URL pointing to the Sentinel Spring Boot container
const API_BASE = 'http://localhost:8080/api';

const rl = readline.createInterface({
  input: process.stdin,
  output: process.stdout,
  terminal: false
});

// Helper to log logs/errors to stderr so they don't pollute the JSON-RPC on stdout
function logError(msg) {
  console.error(`[MCP Error] ${msg}`);
}

async function fetchAPI(path, options = {}) {
  const url = `${API_BASE}${path}`;
  try {
    const res = await fetch(url, options);
    if (!res.ok) {
      throw new Error(`HTTP ${res.status} ${res.statusText}`);
    }
    return await res.json();
  } catch (err) {
    logError(`Fetch failed for ${url}: ${err.message}`);
    throw err;
  }
}

rl.on('line', async (line) => {
  if (!line.trim()) return;
  try {
    const request = JSON.parse(line);
    const response = await handleRequest(request);
    if (response) {
      console.log(JSON.stringify(response));
    }
  } catch (err) {
    logError(`Failed to process input: ${err.message}`);
  }
});

async function handleRequest(req) {
  const { jsonrpc, id, method, params } = req;
  
  if (method === 'initialize') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        protocolVersion: '2024-11-05',
        capabilities: {
          tools: {}
        },
        serverInfo: {
          name: 'server-sentinel-mcp',
          version: '1.0.0'
        }
      }
    };
  }
  
  if (method === 'notifications/initialized') {
    return null;
  }
  
  if (method === 'tools/list') {
    return {
      jsonrpc: '2.0',
      id,
      result: {
        tools: [
          {
            name: 'get_system_stats',
            description: 'Lấy thông số tải CPU, RAM, Ổ cứng, Tốc độ mạng (Rx/Tx) và card đồ họa GPU hiện tại.',
            inputSchema: { type: 'object', properties: {} }
          },
          {
            name: 'get_system_history',
            description: 'Lấy lịch sử hiệu suất máy chủ trong 30 phút gần nhất.',
            inputSchema: { type: 'object', properties: {} }
          },
          {
            name: 'list_containers',
            description: 'Liệt kê danh sách tất cả các Docker container trên máy chủ.',
            inputSchema: { type: 'object', properties: {} }
          },
          {
            name: 'manage_container',
            description: 'Điều khiển trạng thái container (Khởi động, Dừng, Khởi động lại).',
            inputSchema: {
              type: 'object',
              properties: {
                containerId: { type: 'string', description: 'ID của container' },
                action: { type: 'string', enum: ['start', 'stop', 'restart'], description: 'Hành động thực thi' }
              },
              required: ['containerId', 'action']
            }
          },
          {
            name: 'get_container_logs',
            description: 'Đọc nhật ký logs của một container cụ thể.',
            inputSchema: {
              type: 'object',
              properties: {
                containerId: { type: 'string', description: 'ID của container' },
                lines: { type: 'number', default: 100, description: 'Số dòng log cần tải' }
              },
              required: ['containerId']
            }
          },
          {
            name: 'toggle_auto_heal',
            description: 'Bật hoặc tắt cơ chế tự phục hồi (Auto-heal) cho một container cụ thể.',
            inputSchema: {
              type: 'object',
              properties: {
                containerName: { type: 'string', description: 'Tên của container' }
              },
              required: ['containerName']
            }
          },
          {
            name: 'update_settings',
            description: 'Cập nhật cài đặt cấu hình cảnh báo tài nguyên hệ thống và Discord Webhook URL.',
            inputSchema: {
              type: 'object',
              properties: {
                cpuThreshold: { type: 'number', description: 'Ngưỡng CPU Load cảnh báo (%)' },
                ramThresholdMB: { type: 'number', description: 'Ngưỡng dung lượng RAM trống tối thiểu (MB)' },
                discordWebhookUrl: { type: 'string', description: 'Đường dẫn Discord Webhook mới' }
              }
            }
          }
        ]
      }
    };
  }
  
  if (method === 'tools/call') {
    const { name, arguments: args } = params;
    try {
      let resultText = '';
      if (name === 'get_system_stats') {
        const stats = await fetchAPI('/system/stats');
        resultText = JSON.stringify(stats, null, 2);
      } else if (name === 'get_system_history') {
        const history = await fetchAPI('/system/history');
        resultText = JSON.stringify(history, null, 2);
      } else if (name === 'list_containers') {
        const containers = await fetchAPI('/containers');
        resultText = JSON.stringify(containers.map(c => ({
          id: c.Id || c.id,
          names: c.Names || c.names,
          image: c.Image || c.image,
          state: c.State || c.state,
          status: c.Status || c.status
        })), null, 2);
      } else if (name === 'manage_container') {
        const { containerId, action } = args;
        const res = await fetchAPI(`/containers/${containerId}/${action}`, { method: 'POST' });
        resultText = JSON.stringify(res, null, 2);
      } else if (name === 'get_container_logs') {
        const { containerId, lines = 100 } = args;
        const logs = await fetchAPI(`/containers/${containerId}/logs?lines=${lines}`);
        resultText = logs.logs || 'Không có logs.';
      } else if (name === 'toggle_auto_heal') {
        const { containerName } = args;
        const res = await fetchAPI('/auto-heal/whitelist/toggle', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ name: containerName })
        });
        resultText = JSON.stringify(res, null, 2);
      } else if (name === 'update_settings') {
        const res = await fetchAPI('/settings', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(args)
        });
        resultText = JSON.stringify(res, null, 2);
      } else {
        return {
          jsonrpc: '2.0',
          id,
          error: { code: -32601, message: `Tool ${name} not found` }
        };
      }
      
      return {
        jsonrpc: '2.0',
        id,
        result: {
          content: [
            {
              type: 'text',
              text: resultText
            }
          ]
        }
      };
    } catch (err) {
      return {
        jsonrpc: '2.0',
        id,
        error: { code: -32000, message: `Loi thuc thi cong cu: ${err.message}` }
      };
    }
  }
  
  return {
    jsonrpc: '2.0',
    id,
    error: { code: -32601, message: `Method ${method} not found` }
  };
}
