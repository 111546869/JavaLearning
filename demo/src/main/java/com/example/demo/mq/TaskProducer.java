package com.example.demo.mq;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskProducer {
    private final RabbitTemplate rabbitTemplate;

    public TaskProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void send(TaskMessage message) {
        rabbitTemplate.convertAndSend(MqConfig.EXCHANGE, MqConfig.ROUTING, message);
    }
}
