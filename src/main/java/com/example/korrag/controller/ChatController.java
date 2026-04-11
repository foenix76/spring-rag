package com.example.korrag.controller;

import com.example.korrag.entity.ChatMessage;
import com.example.korrag.service.RagService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final RagService ragService;

    public ChatController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public Map<String, Object> chat(@RequestBody Map<String, String> request) {
        String userId = request.getOrDefault("userId", "HR_USER_01");
        String message = request.get("message");
        return ragService.ask(userId, message);
    }

    @GetMapping("/chat/history")
    public List<ChatMessage> getHistory(@RequestParam(value = "userId", defaultValue = "HR_USER_01") String userId) {
        return ragService.getChatHistory(userId);
    }

    @DeleteMapping("/chat/history")
    public void clearHistory(@RequestParam(value = "userId", defaultValue = "HR_USER_01") String userId) {
        ragService.clearChatHistory(userId);
    }
}
