package com.prakash.taskmaster.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

// Represents scheduling info for a single task within the AI's suggestion
@Data
@NoArgsConstructor // Required for BeanOutputParser
public class ScheduledTaskInfo {
    // Ensure these field names match the format instruction in the prompt
    private String taskId;
    private LocalDateTime suggestedStartTime; // The AI-recommended start time
    // Optional: AI could provide an estimated end time too
    // private LocalDateTime suggestedEndTime;
}