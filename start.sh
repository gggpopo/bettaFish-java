#!/bin/bash
# BettaFish 启动脚本
# 使用方法: ./start.sh

set -e

echo "========================================="
echo "  BettaFish 舆情分析系统 启动脚本"
echo "========================================="

# 检查 .env 文件
if [ ! -f .env ]; then
    echo "[!] 未找到 .env 文件"
    echo "    请先复制 .env.example 为 .env 并填入 API Key:"
    echo "    cp .env.example .env"
    echo "    然后编辑 .env 填入你的 API Key"
    exit 1
fi

# 检查 Java 版本
java_version=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$java_version" -lt 21 ] 2>/dev/null; then
    echo "[!] 需要 Java 21+，当前版本: $(java -version 2>&1 | head -1)"
    exit 1
fi
echo "[OK] Java $(java -version 2>&1 | head -1 | cut -d'"' -f2)"

# 加载环境变量
export $(grep -v '^#' .env | grep -v '^$' | xargs)

# 构建项目
echo ""
echo "[1/3] 构建项目..."
./mvnw package -DskipTests -q
echo "[OK] 构建完成"

# 启动 Sentiment MCP 服务 (后台)
echo ""
echo "[2/3] 启动 Sentiment MCP 服务 (端口 8081)..."
java -jar bettafish-sentiment-mcp/target/*.jar --server.port=8081 &
SENTIMENT_PID=$!
echo "[OK] Sentiment MCP PID: $SENTIMENT_PID"

# 等待 Sentiment MCP 就绪
sleep 3

# 启动主应用
echo ""
echo "[3/3] 启动 BettaFish 主应用 (端口 8080)..."
echo ""
echo "========================================="
echo "  API 端点:"
echo "  POST http://localhost:8080/api/analysis"
echo "  GET  http://localhost:8080/api/analysis/{taskId}"
echo "  GET  http://localhost:8080/api/analysis/{taskId}/events (SSE)"
echo ""
echo "  测试命令:"
echo "  curl -X POST http://localhost:8080/api/analysis \\"
echo "    -H 'Content-Type: application/json' \\"
echo "    -d '{\"query\": \"武汉大学樱花季舆情分析\"}'"
echo "========================================="
echo ""

java -jar bettafish-app/target/*.jar

# 清理
kill $SENTIMENT_PID 2>/dev/null || true
