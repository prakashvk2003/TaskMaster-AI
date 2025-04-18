package com.prakash.taskmaster.dto;

import lombok.Data;

@Data
public class TaskSuggestion {
    private String title;
    private String description;
    private String date;
    private String time;
}
