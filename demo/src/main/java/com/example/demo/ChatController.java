package com.example.demo;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    private final DifyService difyService;

    public ChatController(DifyService difyService) {
        this.difyService = difyService;
    }

    // 注意：produces 必须指定为 TEXT_EVENT_STREAM_VALUE
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@RequestBody ChatRequest request) {
        // 直接将 Service 层的异步流返回给前端
        return difyService.streamChatFromDify(request.getMessage());
    }
}