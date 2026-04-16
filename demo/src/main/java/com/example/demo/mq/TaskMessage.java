package com.example.demo.mq;

import java.io.Serializable;

public class TaskMessage implements Serializable {
    public String taskId;
    public String type;
    public String payload;
    public String replyTo;

    public TaskMessage() {}

    public TaskMessage(String taskId, String type, String payload) {
        this.taskId = taskId;
        this.type = type;
        this.payload = payload;
    }
}
