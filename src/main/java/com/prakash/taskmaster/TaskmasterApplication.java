package com.prakash.taskmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing; // If you want @CreatedDate etc.
import org.springframework.scheduling.annotation.EnableScheduling; // Add this annotation

@SpringBootApplication
@EnableScheduling // Enable Spring's scheduled task execution
@EnableMongoAuditing // Optional: Enable automatic setting of @CreatedDate, @LastModifiedDate in Task entity
public class TaskmasterApplication {

	public static void main(String[] args) {
		SpringApplication.run(TaskmasterApplication.class, args);
	}

}