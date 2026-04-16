package com.example.demo.mq;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.example.demo.sse.EmitterRegistry;

@Component
public class TaskConsumer {

    @RabbitListener(queues = MqConfig.QUEUE)
    public void onMessage(TaskMessage message) {
        String result = "执行完毕 (Mock)\n提交的代码：\n" + message.payload;
        // 回送消息
        EmitterRegistry.getEmitter(message.taskId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event().data(result));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });
    }
}
