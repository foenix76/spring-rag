package com.example.korrag.service;

import org.springframework.stereotype.Component;
import reactor.core.publisher.FluxSink;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ToolEventPublisher {

    private final ConcurrentHashMap<String, FluxSink<Map<String, Object>>> activeSinks = new ConcurrentHashMap<>();

    public void registerSink(String userId, FluxSink<Map<String, Object>> sink) {
        activeSinks.put(userId, sink);
    }

    public void removeSink(String userId) {
        activeSinks.remove(userId);
    }

    public void publishEvent(String userId, Map<String, Object> event) {
        FluxSink<Map<String, Object>> sink = activeSinks.get(userId);
        if (sink != null && !sink.isCancelled()) {
            sink.next(event);
        }
    }
}
