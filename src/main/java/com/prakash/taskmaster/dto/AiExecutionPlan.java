package com.prakash.taskmaster.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor // Required for BeanOutputParser
public class AiExecutionPlan {

    // List of steps describing how to execute the task
    // Ensure this field name matches the format instruction in the prompt
    private List<String> executionSteps;

    // Optional: Could add estimated time per step, required resources, etc. later
    // private String requiredResources;
    // private String potentialBlockers;
}