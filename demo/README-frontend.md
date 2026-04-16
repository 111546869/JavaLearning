# 稳健理财 Agent — 前端演示

已为本项目添加一个简单的静态前端，文件路径：

- `src/main/resources/static/index.html` — 主页面
- `src/main/resources/static/app.js` — 流式请求并显示 SSE 的客户端逻辑
- `src/main/resources/static/styles.css` — 简单样式

运行后在浏览器打开 http://localhost:8080/ 即可访问页面并与后端 Agent 流式交互。

快速运行：

```bash
# 项目根目录
./mvnw spring-boot:run

# 或者先打包再运行
./mvnw -DskipTests package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

注意：浏览器端使用 fetch + stream 读取 POST 返回的 text/event-stream；EventSource 不支持 POST，所以需要本实现。
