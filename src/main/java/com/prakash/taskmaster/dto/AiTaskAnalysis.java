package com.prakash.taskmaster.dto;

import com.prakash.taskmaster.model.TaskPriority;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor // Necessary for BeanOutputParser
public class AiTaskAnalysis {

    // Ensure property names match expected JSON fields from the AI prompt's format instruction
    private TaskPriority priority;
    private Integer estimatedDurationMinutes;
    private List<String> suggestedSubtasks;

    // Optional: Add a field for general reasoning or notes from the AI?
    // private String analysisNotes;
}