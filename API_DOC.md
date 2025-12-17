# Better Monitor Agent API Documentation (Detailed)

This document describes the communication protocol among the Agent and the Dashboard (Server). It is intended for third-party implementations (e.g., Android Agent).

**Note**: All JSON keys are case-sensitive. Timestamps are usually Unix timestamps (seconds or milliseconds as specified).

---

## 1. Connection & Authentication

### 1.1. Registration (Optional)
If the agent only has a `Register Token` but has NOT yet been assigned a `Server ID` and `Secret Key`, it must register first.

- **Method**: `POST`
- **URL**: `{ServerURL}/api/server/register` (Note: inferred from agent logic)
- **Headers**:
    - `Authorization`: `Bearer {RegisterToken}`
    - `Content-Type`: `application/json` (if body is sent)
- **Response**:
    ```json
    {
      "code": 200,
      "message": "success",
      "data": {
        "server_id": 123,
        "secret_key": "your-permanent-secret-key"
      }
    }
    ```
    *Note: The agent implementation calls `client.RegisterAgent`. Save the `server_id` and `secret_key` for future connections.*

### 1.2. WebSocket Connection
The core communication happens over a persistent WebSocket connection.

- **Connection URL Construction**:
    1.  Clean the base URL (remove `http://` or `https://`).
    2.  Use `wss://` (if original was `https`) or `ws://` (if `http`).
    3.  Append path: `/api/servers/{ServerID}/ws`
    4.  Append Query Param: `?token={SecretKey}`

    **Example**:
    `wss://monitor.example.com/api/servers/101/ws?token=sk_abc123xyz`

- **Fallback Paths**: If the above fails, try:
    -   `/servers/{ServerID}/ws`
    -   `/api/ws/{ServerID}/server`
    -   `/ws/{ServerID}/server`

### 1.3. Settings Synchronization (HTTP)
The agent periodically (default every 1 minute) synchronizes its configuration from the server.

- **Method**: `GET`
- **URL**: `{ServerURL}/api/servers/{ServerID}/settings`
- **Headers**:
    -   `X-Secret-Key`: `{SecretKey}`
    -   `Content-Type`: `application/json`

- **Response JSON**:
    ```json
    {
      "success": true,
      "message": "Settings retrieved",
      "server_id": 101,
      "secret_key": "sk_abc123xyz", 
      "heartbeat_interval": "10s",
      "monitor_interval": "30s",
      "agent_release_repo": "EnderKC/BetterMonitor",
      "agent_release_channel": "stable",
      "agent_release_mirror": "https://mirror.ghproxy.com/"
    }
    ```
    *Client Action*: If `secret_key` changes, update local storage. If intervals change, update internal timers.

---

## 2. Heartbeat Mechanism

The heartbeat keeps the connection alive and reports basic status.

### 2.1. Client -> Server (Initial & Periodic)
Sent immediately after connection and periodically (default 10s).

**Request**:
```json
{
  "type": "heartbeat",
  "timestamp": 1678900000,   // Current Unix Timestamp (seconds)
  "status": "online",        // Always "online"
  "version": "1.0.0",        // Agent Version
  "is_reply": false          // False for proactive heartbeats
}
```

### 2.2. Server -> Client (Check)
The server may send a heartbeat to check if the agent is alive.

**Incoming Message**:
```json
{
  "type": "heartbeat",
  "timestamp": 1678900010
}
```

### 2.3. Client -> Server (Reply)
Upon receiving a heartbeat from the server, the agent **MUST** reply.

**Response**:
```json
{
  "type": "heartbeat",
  "timestamp": 1678900010,   // Current timestamp
  "status": "online",
  "version": "1.0.0",
  "is_reply": true           // TRUE to indicate this is a reply
}
```

---

## 3. System Monitoring

### 3.1. System Info (Sent Once)
Sent immediately after the WebSocket connection is established (and after reconnection).

**Message**:
```json
{
  "type": "system_info",
  "payload": {
    "hostname": "pixel-7-pro",
    "os": "android",
    "platform": "android",
    "platform_version": "13",
    "kernel_version": "5.10.0-android12-9",
    "kernel_arch": "aarch64",
    "cpu_model": "Google Tensor G2",
    "cpu_cores": 8,
    "memory_total": 12000000000,  // Bytes
    "disk_total": 128000000000,   // Bytes
    "boot_time": 1678000000,      // Bytes
    "public_ip": "203.0.113.1"
  }
}
```

### 3.2. Monitor Data (Periodic)
Sent every `monitor_interval` (default 30s).

**Message**:
```json
{
  "type": "monitor",
  "payload": {
    "cpu_usage": 12.5,            // % (0-100)
    "memory_used": 4000000000,    // Bytes
    "memory_total": 12000000000,  // Bytes
    "disk_used": 30000000000,     // Bytes
    "disk_total": 128000000000,   // Bytes
    "network_in": 10240.0,        // Bytes/sec (Input Rate)
    "network_out": 5120.0,        // Bytes/sec (Output Rate)
    "load_avg_1": 1.5,
    "load_avg_5": 1.2,
    "load_avg_15": 1.0,
    "swap_used": 0,
    "swap_total": 0,
    "boot_time": 1678000000,
    "latency": 45.0,              // Ping latency to server (ms)
    "packet_loss": 0.0            // Packet loss % to server
  }
}
```

---

## 4. File Management Operations

The server sends requests to manage files. The agent must process them and send a response with the **same `request_id`**.

### 4.1. List Directory (`file_list`)
**Request (Server -> Client)**:
```json
{
  "type": "file_list",
  "request_id": "req-001",
  "payload": {
    "path": "/sdcard/Download"
  }
}
```

**Response (Client -> Server)**:
- **Type**: `file_list_response`
```json
{
  "type": "file_list_response",
  "request_id": "req-001",
  "data": {
    "path": "/sdcard/Download",
    "files": [
      {
        "name": "image.jpg",
        "size": 10240,
        "is_dir": false,
        "mod_time": "2023-01-01T12:00:00Z", // RFC3339 or similar string
        "mode": "-rw-r--r--"
      },
      {
        "name": "Videos",
        "size": 4096,
        "is_dir": true,
        "mod_time": "2023-01-02T12:00:00Z",
        "mode": "drwxr-xr-x"
      }
    ]
  }
}
```

### 4.2. Get File Content (`file_content`: get)
**Request**:
```json
{
  "type": "file_content",
  "request_id": "req-002",
  "payload": {
    "path": "/sdcard/config.txt",
    "action": "get"
  }
}
```

**Response**:
- **Type**: `file_content_response`
```json
{
  "type": "file_content_response",
  "request_id": "req-002",
  "data": {
    "path": "/sdcard/config.txt",
    "content": "file content string..." // Text content
  }
}
```
*Error Response*:
```json
{
  "type": "error",
  "request_id": "req-002",
  "data": { "error": "file not found" }
}
```

### 4.3. Save File Content (`file_content`: save)
**Request**:
```json
{
  "type": "file_content",
  "request_id": "req-003",
  "payload": {
    "path": "/sdcard/config.txt",
    "action": "save",
    "content": "new content..."
  }
}
```

**Response**:
- **Type**: `file_content_response`
```json
{
  "type": "file_content_response",
  "request_id": "req-003",
  "data": {
    "path": "/sdcard/config.txt",
    "success": true,
    "message": "文件保存成功"
  }
}
```

### 4.4. Create File (`file_content`: create)
**Request**:
```json
{
  "type": "file_content",
  "request_id": "req-004",
  "payload": {
    "path": "/sdcard/newfile.txt",
    "action": "create",
    "content": "" // Optional initial content
  }
}
```

**Response**:
- **Type**: `file_content_response`
```json
{
  "type": "file_content_response",
  "request_id": "req-004",
  "data": {
    "path": "/sdcard/newfile.txt",
    "success": true,
    "message": "文件创建成功"
  }
}
```

### 4.5. Make Directory (`file_content`: mkdir)
**Request**:
```json
{
  "type": "file_content",
  "request_id": "req-005",
  "payload": {
    "path": "/sdcard/NewFolder",
    "action": "mkdir"
  }
}
```

**Response**:
- **Type**: `file_content_response`
```json
{
  "type": "file_content_response",
  "request_id": "req-005",
  "data": {
    "path": "/sdcard/NewFolder",
    "success": true,
    "message": "目录创建成功"
  }
}
```

### 4.6. Directory Tree (`tree`)
**Request**:
```json
{
  "type": "tree",
  "request_id": "req-006",
  "payload": {
    "path": "/sdcard",
    "content": "3" // Depth (as string)
  }
}
```

**Response**:
- **Type**: `file_tree_response`
```json
{
  "type": "file_tree_response",
  "request_id": "req-006",
  "data": {
    "path": "/sdcard",
    "tree": [
      {
        "name": "Download",
        "size": 4096,
        "mod_time": "2023-01-01T10:00:00Z",
        "is_dir": true,
        "mode": "drwxrwx---",
        "children": [
          {
            "name": "image.jpg",
            "size": 1024,
            "mod_time": "2023-01-01T10:05:00Z",
            "is_dir": false,
            "mode": "-rw-rw----"
          }
        ]
      },
      {
        "name": "notes.txt",
        "size": 128,
        "mod_time": "2023-01-02T11:00:00Z",
        "is_dir": false,
        "mode": "-rw-rw----"
      }
    ]
  }
}
```
IMPORTANT

The tree field MUST be a JSON Array []. It cannot be null or omitted, even if the directory is empty. If the directory is empty, return "tree": []. The backend will treat null or missing field as an "Invalid file tree format" error.

### 4.7. Upload File (`file_upload`)
**Request**:
```json
{
  "type": "file_upload",
  "request_id": "req-007",
  "payload": {
    "path": "/sdcard/Uploads",
    "filename": "uploaded.jpg",
    "content": "base64_encoded_string..."
  }
}
```

**Response**:
- **Type**: `file_upload_response`
```json
{
  "type": "file_upload_response",
  "request_id": "req-007",
  "data": {
    "path": "/sdcard/Uploads",
    "filename": "uploaded.jpg",
    "success": true,
    "message": "文件上传成功"
  }
}
```

---

## 5. Terminal / WebShell Operations

This supports interactive shell sessions. For Android, this might connect to `/system/bin/sh` or a similar local shell.

### 5.1. Create Session (`terminal_create` & `shell_command: create`)
The server can request a session creation via two message types (legacy support). Handle both if possible.

**Request (Legacy)**:
```json
{
  "type": "terminal_create",
  "session_id": "sess-abc"
}
```

**Request (Standard)**:
```json
{
  "type": "shell_command",
  "payload": {
    "type": "create",
    "session": "sess-abc"
  }
}
```

**Action**: Spawn a PTY/Shell process. No immediate JSON response required, but you should start sending output.

### 5.2. Terminal Input (`terminal_input` & `shell_command: input`)
User keystrokes from the dashboard.

**Request**:
```json
{
  "type": "shell_command",
  "payload": {
    "type": "input",
    "session": "sess-abc",
    "data": "ls -la\n"  // The actual input string
  }
}
```
*Legacy format `terminal_input` also exists with `input` field.*

### 5.3. Resize Terminal (`terminal_resize` & `shell_command: resize`)
**Request**:
```json
{
  "type": "shell_command",
  "payload": {
    "type": "resize",
    "session": "sess-abc",
    "data": "{\"cols\": 80, \"rows\": 24}" // JSON string inside data
  }
}
```

### 5.4. Close Session (`terminal_close` & `shell_command: close`)
**Request**:
```json
{
  "type": "shell_command",
  "payload": {
    "type": "close",
    "session": "sess-abc"
  }
}
```

**Response (Client -> Server)**:
```json
{
  "type": "shell_close",
  "session": "sess-abc",
  "message": "终端会话已关闭"
}
```

### 5.5. Terminal Output (Client -> Server)
When the shell produces output (stdout/stderr), stream it to the server.

**Message**:
```json
{
  "type": "shell_response",
  "session": "sess-abc",
  "data": "root@android:/ # " // Raw output chunk
}
```

### 5.6. Terminal Error (Client -> Server)
If spawning fails or IO error occurs.
**Message**:
```json
{
  "type": "shell_error",
  "session": "sess-abc",
  "error": "Failed to spawn shell: permission denied"
}
```

---

## 6. Process Management

### 6.1. List Processes (`process_list`)
**Request**:
```json
{
  "type": "process_list",
  "request_id": "req-008",
  "payload": { "action": "list" } // Action might be empty
}
```

**Response**:
- **Type**: `process_list_response`
```json
{
  "type": "process_list_response",
  "request_id": "req-008",
  "data": {
    "timestamp": 1678900000,
    "count": 2,
    "processes": [
      {
        "pid": 1,
        "ppid": 0,
        "name": "init",
        "username": "root",
        "memory": 1024000,
        "cpu": 0.1,
        "status": "Running",
        "cmdline": "/init"
      },
      ...
    ]
  }
}
```

### 6.2. Kill Process (`process_kill`)
**Request**:
```json
{
  "type": "process_kill",
  "request_id": "req-009",
  "payload": {
    "pid": 1234
  }
}
```

**Response**:
- **Type**: `process_kill_response`
```json
{
  "type": "process_kill_response",
  "request_id": "req-009",
  "data": {
    "pid": 1234,
    "name": "target_process",
    "success": true,
    "message": "进程已成功终止",
    "timestamp": 1678900000
  }
}
```

---

## 7. Other Modules (Brief)

These modules are less relevant for a standard Android implementation unless you run a Docker environment on it (e.g., Termux with root).

### 7.1. Docker Operations
- **Request Type**: `docker_command`, `docker_file`, `docker_images`, `docker_composes`
- **Response Types**: `docker_containers`, `docker_images`, `docker_composes`, `docker_file_*`
- **Functionality**: Create/List/Remove containers, images, and compose stacks.

### 7.2. Nginx Operations
- **Request Type**: `nginx_command`
- **Response Types**: `nginx_success`, `nginx_error`
- **Functionality**: Parse Nginx config, reload service, get status.

---

## 8. General Error Handling
If any request cannot be processed (e.g., unknown type, or internal error), return a generic error.

**Response**:
```json
{
  "type": "error",
  "request_id": "req-xyz",
  "data": {
    "error": "Detailed error message"
  }
}
```
