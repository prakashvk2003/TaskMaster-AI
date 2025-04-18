package com.prakash.taskmaster.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Represents the overall suggestion from the AI
@Data
@NoArgsConstructor
public class AiScheduleSuggestion {
    // List of tasks with their suggested schedule details
    // Ensure this field name matches the format instruction in the prompt
    private List<ScheduledTaskInfo> scheduledTasks;

    // Optional: Add reasoning or summary from the AI
    // private String schedulingRationale;
}