package com.example.todo.label.controller;

import com.example.todo.label.dto.*;
import com.example.todo.label.service.LabelService;

import com.example.todo.auth.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.annotation.AuthenticationPrincipal;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/labels")
@RequiredArgsConstructor
public class LabelController {

    private final LabelService labelService;

    @PostMapping
    public LabelResponse createLabel(

            @AuthenticationPrincipal
            CustomUserDetails user,

            @RequestBody
            CreateLabelRequest request
    ) {

        return labelService.createLabel(user.getUserId(), request);
    }

    @GetMapping
    public List<LabelResponse> getLabels(

            @AuthenticationPrincipal
            CustomUserDetails user
    ) {

        return labelService.getLabels(user.getUserId());
    }
}