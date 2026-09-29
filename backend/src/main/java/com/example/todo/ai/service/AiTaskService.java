package com.example.todo.ai.service;

import com.example.todo.ai.dto.DecomposeTaskRequest;
import com.example.todo.ai.dto.DecomposeTaskResponse;
import com.example.todo.ai.dto.ParsedTaskResponse;
import com.example.todo.ai.dto.ProductivityInsightRequest;
import com.example.todo.ai.dto.ProductivityInsightResponse;

public interface AiTaskService {

    ParsedTaskResponse parseTask(String input);

    ProductivityInsightResponse generateInsights(ProductivityInsightRequest request);

    DecomposeTaskResponse decomposeTask(DecomposeTaskRequest request);
}