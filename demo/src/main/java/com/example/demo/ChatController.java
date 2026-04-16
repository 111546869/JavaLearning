package com.example.demo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agent")
public class ChatController {

    // 线程池：用于处理耗时的 AI 推理任务，避免阻塞主线程
    private final ExecutorService nonBlockingService = Executors.newCachedThreadPool();

    private final DifyService difyService;

    public ChatController(DifyService difyService){ this.difyService = difyService; }

    // ensure the SSE stream is advertised as UTF-8 so clients render Chinese correctly
    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter handleChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        nonBlockingService.execute(() -> {
            try {
                difyService.streamChat(request.message, emitter);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }
}