package com.example.todo.ai.service;

import com.example.todo.ai.dto.ParsedTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class GeminiTaskService implements AiTaskService {

    private final ChatClient chatClient;

    @Override
    public ParsedTaskResponse parseTask(String input) {

        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Kolkata"));

        String prompt = """

        Extract task details from the following input.
        
        Current date and time (Asia/Kolkata):
        %s
        
        Interpret relative dates such as:
        - today
        - tomorrow
        - next week
        - this Friday
        - next Monday
        
        relative to the current date above.
        
        Return ONLY valid JSON.
        
        Required fields:
        - title
        - description
        - priority
        - dueDate
        
        Rules:
        - dueDate must be ISO-8601 LocalDateTime format
        - Use Asia/Kolkata timezone when interpreting dates
        - Priority must be one of: LOW, MEDIUM, HIGH
        - Do not invent information not present in the input
        - If a date is not specified, return null for dueDate
        
        Input:
        %s
        
        """.formatted(now, input);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ParsedTaskResponse.class);
    }
}