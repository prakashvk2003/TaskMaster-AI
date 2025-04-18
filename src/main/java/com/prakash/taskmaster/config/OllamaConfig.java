package com.prakash.taskmaster.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for loading ollama model options from application properties.
 * <p>
 * This class is bound to the property prefix <strong>spring.ai.ollama.chat.options</strong>,
 * allowing centralized access to model-related configuration.
 * </p>
 *
 * Example configuration in <code>application.properties</code>:
 * <pre>
 * spring.ai.ollama.chat.options.model=gemma3:4b
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "spring.ai.ollama.chat.options")
public class OllamaConfig {

    /**
     * The name of the ollama model to be used (e.g., "deep-seek-r1:7b", "gemma3:4b").
     */
    private String model;

    /**
     * Returns the configured ollama model name.
     *
     * @return the model name as configured in application properties
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the ollama model name from application properties.
     *
     * @param model the model name to use
     */
    public void setModel(String model) {
        this.model = model;
    }
}
