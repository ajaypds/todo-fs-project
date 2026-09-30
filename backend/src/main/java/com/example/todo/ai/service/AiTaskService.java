package com.example.todo.ai.service;

import com.example.todo.ai.dto.ParsedTaskResponse;

public interface AiTaskService {

    ParsedTaskResponse parseTask(String input);
}