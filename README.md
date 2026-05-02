# Personal Context Injection (PCI)

> 用 AI 帮你记住、提醒、找到生活中的重要信息。

一个面向个人日程与生活服务的 AI 助手系统。你拍张课程表，它自动识别；你说"明天下午三点交作业"，它自动提醒；你问"之前记的那个事"，它用语义搜索找到。

---

## 快速体验

**默认账号：admin / admin123**

启动后访问 `http://localhost:5173`，直接用默认账号登录即可体验所有功能。

---

## 核心功能

| 功能 | 说明 |
|------|------|
| 课程表管理 | 手动录入 / 拍照 AI 识别导入 |
| 备忘录 | 自然语言输入，自动提取时间，到期提醒 |
| 通知中心 | 备忘录到期自动推送，未读高亮 |
| AI 对话 | 注入今日课程、待办、画像，让 AI 知道你的状态 |
| 出行规划 | 文字/图片输入出行需求，给出公交地铁路线 |
| 攻略中心 | UGC 内容 + 互动评分 |
| 图片路由 | 上传图片自动识别跳转到对应模块 |
| 用户画像 | 沉淀偏好和身份信息，改善 AI 回答质量 |

---

## 核心设计

### 解析 → 预览 → 确认
所有"写入操作"统一两步走，避免 AI 误操作：
1. `ai-parse`：只生成预览，不写数据库
2. 用户确认后，`ai-confirm` 才真正执行

### 上下文注入
每次对话前，自动聚合用户的今日课程、待办备忘、用户画像注入 System Prompt，让 AI 回答贴近你的真实状态。

### RAG 语义搜索
备忘录向量化存入 Qdrant，搜"那个交作业的事"也能找到。

---

## 技术栈

**后端**：Spring Boot 2.3.12 / MyBatis-Plus / MySQL 8 / Redis / Qdrant / Redisson

**前端**：Vue 3 / TypeScript / Vite

**AI 服务**：
- 智谱 AI `glm-4-flash`（对话）/ `embedding-2`（向量化）
- 通义千问 VL（图片识别）
- 高德 WebService（地理编码 / 路线规划）

---

## 本地运行

### 环境要求

- JDK 8+
- Node.js 20+
- MySQL 8（建库 `pci`，执行 `backend/src/main/resources/init.sql`）
- Redis（默认 `127.0.0.1:6379`）
- Qdrant（默认端口 `6334`）

### 配置

复制配置模板并填入你自己的 API key：

```bash
cp backend/src/main/resources/application-example.yaml \
   backend/src/main/resources/application.yaml
```

需要填写的 key：
- `zhipu.api-key`：[智谱 AI](https://open.bigmodel.cn)
- `vision.api-key`：[阿里云通义 VL](https://dashscope.aliyuncs.com)
- `gaode.api-key`：[高德开放平台](https://lbs.amap.com)

### 启动

**后端（IDEA 直接运行 PciApplication，启动后自动打开浏览器）：**

```bash
cd backend
mvn spring-boot:run
```

**前端：**

```bash
cd frontend
npm install
npm run dev
```

启动后访问 `http://localhost:5173`，默认账号 `admin / admin123`。

---

## 目录结构

```
PersonalContextInjection/
├── backend/
│   └── src/main/
│       ├── java/com/pci/
│       │   ├── controller/   # 接口层
│       │   ├── service/      # 业务逻辑
│       │   ├── entity/       # 数据模型
│       │   └── config/       # 配置类
│       └── resources/
│           ├── application-example.yaml  # 配置模板（提交到仓库）
│           ├── init.sql                  # 建表脚本
│           └── redisson.yaml
├── frontend/
│   └── src/
│       ├── views/            # 页面组件
│       ├── components/       # 通用组件
│       ├── router/           # 路由
│       └── stores/           # Pinia 状态
└── scripts/                  # 启停脚本
```

---

## License

仅用于学习与研究。
