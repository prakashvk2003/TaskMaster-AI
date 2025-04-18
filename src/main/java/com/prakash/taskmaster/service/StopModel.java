package com.prakash.taskmaster.service;

import com.prakash.taskmaster.config.OllamaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * StopModel is a service responsible for attempting to stop the ollama model process after use.
 * It helps manage system resources by trying to terminate the model instance once a task is completed.
 *
 * NOTE: Forcefully stopping the main Ollama process (`ollama stop <model>`) after every request
 * can be inefficient and may negatively impact performance, especially under load, as it involves
 * process startup/shutdown overhead and potentially unloading/reloading models.
 * Consider if this is truly necessary for your use case or if letting Ollama manage its own
 * lifecycle is preferable. This might be useful in highly resource-constrained environments
 * or for specific debugging scenarios.
 *
 * The model name is dynamically retrieved from the application properties via the {@link OllamaConfig} class.
 */
@Service
public class StopModel {

    private static final Logger log = LoggerFactory.getLogger(StopModel.class);
    private final OllamaConfig config;

    /**
     * Constructor for injecting ollama configuration.
     *
     * @param config a configuration class that provides the current model name from application properties
     */
    public StopModel(OllamaConfig config) {
        this.config = config;
    }

    /**
     * Attempts to stop the currently configured ollama model by invoking the `ollama stop <model>` command.
     * This method helps free up memory and CPU usage after model usage, but has performance implications.
     */
    public void stopModel() {
        String modelName = config.getModel();
        if (modelName == null || modelName.isBlank()) {
            log.warn("Cannot stop Ollama model: model name is not configured.");
            return;
        }

        log.info("Attempting to stop Ollama model: {}", modelName);
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("ollama", "stop", modelName);
            // Redirect error stream to inherit IO so errors appear in Spring Boot console
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();

            // Optional: Wait for the process to finish with a timeout
            boolean finished = process.waitFor(10, TimeUnit.SECONDS); // Wait up to 10 seconds

            if (finished && process.exitValue() == 0) {
                log.info("Successfully sent stop command for Ollama model: {}", modelName);
            } else if (!finished) {
                log.warn("Stop command for Ollama model '{}' timed out. Forcing destruction.", modelName);
                process.destroyForcibly();
            } else {
                log.error("Stop command for Ollama model '{}' failed with exit code: {}", modelName, process.exitValue());
                // Consider reading the process output/error stream here for more details
            }

        } catch (IOException e) {
            log.error("IOException occurred while trying to stop Ollama model '{}': {}", modelName, e.getMessage(), e);
        } catch (InterruptedException e) {
            log.error("InterruptedException occurred while waiting for Ollama stop command for model '{}': {}", modelName, e.getMessage(), e);
            Thread.currentThread().interrupt(); // Restore interrupted status
        } catch (SecurityException e) {
            log.error("SecurityException: Not allowed to execute external process 'ollama stop {}': {}", modelName, e.getMessage(), e);
        } catch (Exception e) {
            // Catch unexpected exceptions
            log.error("Unexpected error occurred while stopping Ollama model '{}': {}", modelName, e.getMessage(), e);
        }
    }
}