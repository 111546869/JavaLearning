package com.example.demo;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.Map;

@Service
public class DifyService {

    private final WebClient webClient;

    public DifyService(WebClient.Builder webClientBuilder) {
        // 配置 Dify 的基础 URL
        this.webClient = webClientBuilder.baseUrl("https://api.dify.ai/v1").build();
    }

    // 返回 Flux<String>，代表一个异步的字符串数据流
    public Flux<String> streamChatFromDify(String userMessage) {
        // 构造 Dify 所需的请求体
        Map<String, Object> requestBody = Map.of(
            "inputs", Map.of(),
            "query", userMessage,
            "response_mode", "streaming",
            "conversation_id", "",
            "user", "web-user"
        );

        return webClient.post()
                .uri("/chat-messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer app-VrnsN1Z0CWYmJbKBC05RW6ts")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                // 直接将 Dify 响应的 SSE 数据流映射为后端的 Flux 通道
                .bodyToFlux(String.class)
                .doOnNext(chunk -> System.out.println("收到 Dify 数据包: " + System.currentTimeMillis() + " -> " + chunk))
                .onErrorResume(e -> {
                    e.printStackTrace();
                    return Flux.just("data: {\"answer\": \"【系统提示】请求 Dify 失败：" + e.getMessage() + "\"}\n\n");
                }); 
    }
}