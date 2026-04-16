package com.example.demo.sse;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class EmitterRegistry {
    private static final ConcurrentHashMap<String, SseEmitter> map = new ConcurrentHashMap<>();

    public static void register(String id, SseEmitter emitter) {
        map.put(id, emitter);
    }

    public static Optional<SseEmitter> getEmitter(String id) {
        return Optional.ofNullable(map.get(id));
    }

    public static void remove(String id) {
        map.remove(id);
    }
}
