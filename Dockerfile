# 银行AI网关 - Docker构建镜像
# 多阶段构建：构建阶段 + 运行阶段

# ==================== 构建阶段 ====================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /build

# 安装Maven
RUN apk add --no-cache maven

# 复制Maven配置文件（利用Docker缓存层）
COPY pom.xml .

# 下载依赖（这一层会被缓存）
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用（跳过测试以加速构建）
RUN mvn clean package -DskipTests -B

# 解压JAR以便后续创建分层镜像
RUN java -Djarmode=layertools -jar target/*.jar extract --destination extracted

# ==================== 运行阶段 ====================
FROM eclipse-temurin:17-jre-alpine

LABEL maintainer="Bank AI Gateway Team"
LABEL version="1.0.0"
LABEL description="银行AI服务统一入口网关"

WORKDIR /app

# 创建非root用户运行应用
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# 从构建阶段复制分层文件
COPY --from=builder /build/extracted/dependencies/ ./
COPY --from=builder /build/extracted/spring-boot-loader/ ./
COPY --from=builder /build/extracted/snapshot-dependencies/ ./
COPY --from=builder /build/extracted/application/ ./

# 设置文件权限
RUN chown -R appuser:appgroup /app

# 切换到非root用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# JVM参数优化
ENV JAVA_OPTS="-Xms512m -Xmx1024m \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=Asia/Shanghai"

# 启动应用
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]