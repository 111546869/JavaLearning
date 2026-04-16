package com.example.demo;

import java.util.Map;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.mq.TaskMessage;
import com.example.demo.mq.TaskProducer;
import com.example.demo.sse.EmitterRegistry;

@RestController
@RequestMapping("/api/v1")
public class TaskController {

    private final TaskProducer taskProducer;

    public TaskController(TaskProducer taskProducer) {
        this.taskProducer = taskProducer;
    }

    @PostMapping("/evaluate")
    public String submitTask(@RequestBody Map<String, String> body) {
        String code = body.get("code");
        String taskId = UUID.randomUUID().toString();
        // 投递任务到 MQ
        taskProducer.send(new TaskMessage(taskId, "run_code", code));
        return taskId;
    }

    @GetMapping("/evaluate/stream")
    public SseEmitter getTaskStream(@RequestParam String taskId) {
        SseEmitter emitter = new SseEmitter(60000L); // 60s 超时
        EmitterRegistry.register(taskId, emitter);
        
        emitter.onCompletion(() -> EmitterRegistry.remove(taskId));
        emitter.onTimeout(() -> EmitterRegistry.remove(taskId));
        emitter.onError(e -> EmitterRegistry.remove(taskId));
        
        return emitter;
    }
}
