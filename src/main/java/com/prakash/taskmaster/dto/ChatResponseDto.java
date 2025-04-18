package com.prakash.taskmaster.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

@Data
@NoArgsConstructor
public class ChatResponseDto {
    private String generation;
    // Add other fields from ChatResponse if needed, e.g., metadata

    public ChatResponseDto(String generation) {
        this.generation = generation;
    }

    // Constructor to map from Spring AI ChatResponse
    public ChatResponseDto(ChatResponse chatResponse) {
        if (chatResponse != null && chatResponse.getResult() != null) {
            Generation generation = chatResponse.getResult();
            this.generation = generation.getOutput().getContent();
            // Map metadata if necessary
        } else {
            this.generation = null; // Or handle appropriately
        }
    }
}