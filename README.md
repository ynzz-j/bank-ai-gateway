# 银行 AI 网关

银行内部 AI 服务的统一入口网关，提供多渠道路由、认证授权、合规过滤、审计日志等能力。

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| Java | 17 LTS | |
| Spring Boot | 3.2.x | WebFlux (响应式) |
| MyBatis-Plus | 3.5.x | ORM |
| PostgreSQL | 16 | 数据库 |
| Redis | 7.x | 缓存/限流 |
| Resilience4j | 2.2.x | 熔断/重试 |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.9+
- Docker 24+ (用于本地数据库)

### 本地开发

```bash
# 1. 启动依赖服务
docker-compose up -d

# 2. 等待服务就绪（约10秒）

# 3. 启动应用（开发环境）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 4. 访问健康检查
curl http://localhost:8080/actuator/health
```

### 默认账号

| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | Admin@123 | 管理员 |

**⚠️ 生产环境必须修改默认密码！**

## 项目结构

```
bank-ai-gateway/
├── src/main/java/com/bank/ai/gateway/
│   ├── config/            # 配置类
│   ├── controller/        # 控制器
│   ├── service/           # 业务逻辑
│   ├── compliance/        # 合规过滤
│   ├── adapter/           # 渠道适配器
│   ├── security/          # 安全
│   ├── ratelimit/         # 限流
│   ├── model/             # 实体类
│   ├── repository/        # 数据访问
│   ├── common/            # 公共组件
│   └── util/              # 工具类
├── src/main/resources/
│   ├── application.yml    # 主配置
│   ├── application-dev.yml
│   ├── application-prod.yml
│   ├── lua/               # Lua脚本
│   └── sql/               # SQL脚本
└── docker-compose.yml
```

## API 接口

### OpenAI 兼容接口

```bash
# 聊天补全
POST /api/v1/chat/completions
Authorization: Bearer sk-xxx

{
  "model": "gpt-4",
  "messages": [{"role": "user", "content": "Hello"}]
}

# 模型列表
GET /api/v1/models
Authorization: Bearer sk-xxx
```

### 认证接口

```bash
# 登录
POST /api/v1/auth/login
{"username": "admin", "password": "Admin@123"}

# 创建API Key
POST /api/v1/auth/keys
Authorization: Bearer <jwt_token>
{"name": "my-key"}
```

### 管理接口

```bash
# 渠道管理
GET|POST|PUT|DELETE /api/v1/admin/channels

# 模型映射
GET|POST|PUT|DELETE /api/v1/admin/model-mappings

# 审计日志
GET /api/v1/admin/audit-logs

# 调用日志
GET /api/v1/admin/call-logs
```

## 配置说明

### 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| DB_HOST | 数据库主机 | localhost |
| DB_PORT | 数据库端口 | 5432 |
| DB_NAME | 数据库名 | bank_ai_gateway |
| DB_USERNAME | 数据库用户名 | postgres |
| DB_PASSWORD | 数据库密码 | postgres |
| REDIS_HOST | Redis主机 | localhost |
| REDIS_PORT | Redis端口 | 6379 |
| REDIS_PASSWORD | Redis密码 | |
| JWT_SECRET | JWT密钥 | (必须设置) |
| KMS_TYPE | KMS类型 | local |
| KMS_LOCAL_KEY | 本地KMS密钥 | |

### 生产环境部署

```bash
# 构建镜像
docker build -t bank-ai-gateway:1.0.0 .

# 运行容器
docker run -d \
  -p 8080:8080 \
  -e DB_HOST=postgres \
  -e DB_PASSWORD=<secure_password> \
  -e REDIS_HOST=redis \
  -e REDIS_PASSWORD=<secure_password> \
  -e JWT_SECRET=<256-bit-secret> \
  -e KMS_TYPE=vault \
  -e VAULT_ADDR=<vault_addr> \
  -e VAULT_TOKEN=<vault_token> \
  bank-ai-gateway:1.0.0
```