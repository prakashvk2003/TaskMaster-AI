package com.prakash.taskmaster.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ChatRequest {
    @NotBlank(message = "Message cannot be empty")
    private String message;
}