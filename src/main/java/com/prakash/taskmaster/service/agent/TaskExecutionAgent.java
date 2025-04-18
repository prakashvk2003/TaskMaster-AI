package com.prakash.taskmaster.service.agent;

import com.prakash.taskmaster.dto.AiExecutionPlan;
import com.prakash.taskmaster.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils; // Import for checking collection emptiness

import java.util.Map;

@Service
public class TaskExecutionAgent {

    private static final Logger log = LoggerFactory.getLogger(TaskExecutionAgent.class);

    private final ChatModel chatModel;

    // Prompt template for generating execution steps
    private final String executionPlanPromptTemplate = """
            You are an expert task execution planner. Given the following task details, generate a concise, step-by-step plan for executing it.
            Focus on actionable steps.

            Task Title: "{title}"
            Task Description: "{description}"
            Task Priority: {priority}
            Suggested Subtasks (for context): {subtasks}

            Generate a list of concrete execution steps needed to complete this task.
            If the task is simple, the list might contain only one or two steps.
            If the task is complex, break it down logically.
            Provide the output as a JSON object matching the requested format.

            {format}
            """;

    @Autowired
    public TaskExecutionAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    /**
     * Generates an execution plan for the given task using the AI model.
     *
     * @param task The task for which to generate an execution plan.
     * @return An AiExecutionPlan containing the list of steps.
     * @throws RuntimeException if the AI interaction or parsing fails.
     */
    public AiExecutionPlan generateExecutionPlan(Task task) {
        log.info("Generating execution plan for Task ID: {}, Title: {}", task.getId(), task.getTitle());

        BeanOutputParser<AiExecutionPlan> outputParser = new BeanOutputParser<>(AiExecutionPlan.class);
        String formatInstructions = outputParser.getFormat();

        String title = task.getTitle();
        String description = task.getDescription();
        String priority = task.getPriority() != null ? task.getPriority().name() : "Not specified";
        String subtasks = !CollectionUtils.isEmpty(task.getSuggestedSubtasks())
                ? String.join(", ", task.getSuggestedSubtasks())
                : "None suggested";

        PromptTemplate promptTemplate = new PromptTemplate(executionPlanPromptTemplate);
        Prompt prompt = promptTemplate.create(Map.of(
                "title", title,
                "description", description,
                "priority", priority,
                "subtasks", subtasks,
                "format", formatInstructions
        ));

        log.debug("Sending execution plan prompt to AI: \n{}", prompt.getInstructions());

        try {
            var chatResponse = chatModel.call(prompt);
            String rawResponse = chatResponse.getResult().getOutput().getContent();
            log.debug("Received raw AI response for execution plan: \n{}", rawResponse);

            AiExecutionPlan executionPlan = outputParser.parse(rawResponse);
            log.info("Successfully parsed AI execution plan for Task ID: {}. Steps generated: {}",
                    task.getId(), executionPlan.getExecutionSteps() != null ? executionPlan.getExecutionSteps().size() : 0);

            // Handle case where AI returns valid JSON but null steps array
            if (executionPlan.getExecutionSteps() == null) {
                log.warn("AI response parsed, but 'executionSteps' list is null for Task ID: {}. Returning plan with null steps.", task.getId());
                // Return the plan as parsed (with null steps)
            }

            return executionPlan;

        } catch (Exception e) {
            log.error("Failed to generate or parse execution plan for Task ID {}: {}", task.getId(), e.getMessage(), e);
            throw new RuntimeException("AI execution plan generation failed for Task ID: " + task.getId(), e);
        }
    }
}