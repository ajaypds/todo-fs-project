package com.example.todo.ai.service;

import com.example.todo.ai.dto.ParsedTaskResponse;
import com.example.todo.ai.dto.ProductivityInsightRequest;
import com.example.todo.ai.dto.ProductivityInsightResponse;
import com.example.todo.exception.PromptSerializationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        
        Current date and time (Asia/Kolkata): %s
        
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
        
        Priority must be returned as a number:
        1 = LOW
        2 = MEDIUM
        3 = HIGH
        4 = URGENT
        
        Priority Rules:
        - URGENT (4): immediate action required, critical issues, deadlines within hours
        - HIGH (3): important tasks with near deadlines
        - MEDIUM (2): normal work tasks
        - LOW (1): optional or non-urgent tasks
        
        The priority field MUST contain only the integer value 1, 2, 3, or 4.
        Do NOT return LOW, MEDIUM, HIGH, or URGENT as text.
        
        Description Rules:
        - description must never be empty
        - If the user does not provide a separate description,
          generate a short description from the title
        - description should be 1-2 sentences maximum
        
        Input: %s
        """.formatted(now, input);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .entity(ParsedTaskResponse.class);
    }

    @Override
    public ProductivityInsightResponse generateInsights(ProductivityInsightRequest request) {

        ObjectMapper objectMapper =  new ObjectMapper();
        String tasksJson;
        try {
            tasksJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new PromptSerializationException("Failed to serialize productivity insight request");
        }
        String prompt = """
        You are a productivity coach. Analyze the provided tasks.
        Return JSON:
        {
          "summary": "...",
          "recommendations": [
            "...",
            "...",
            "..."
          ]
        }
    
        Focus on:
        - overdue tasks
        - workload
        - prioritization
        - planning
    
        Tasks: %s
        """.formatted(tasksJson);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .entity(ProductivityInsightResponse.class);
    }
}