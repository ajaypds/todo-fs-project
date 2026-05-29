package com.example.todo.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.context.annotation.*;


@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(GoogleGenAiChatModel model) {

        return ChatClient.create(model);
    }
}