package com.prakash.taskmaster.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
public class CreateTaskRequest {

    @NotBlank(message = "Task title cannot be blank.")
    @Size(max = 100, message = "Task title cannot exceed 100 characters.")
    private String title;

    @NotBlank(message = "Task description cannot be blank.")
    @Size(max = 1000, message = "Task description cannot exceed 1000 characters.")
    private String description;

    // Allow specifying dependencies during creation
    private Set<String> dependsOnTaskIds;
}