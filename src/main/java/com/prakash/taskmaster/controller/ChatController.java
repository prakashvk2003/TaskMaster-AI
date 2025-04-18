package com.prakash.taskmaster.controller;

import com.prakash.taskmaster.dto.ChatRequest;
import com.prakash.taskmaster.dto.ChatResponseDto;
import com.prakash.taskmaster.service.StopModel;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel; // Use interface
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Temporary ChatController for basic AI interaction testing.
 * Handles AI chat generation requests using the configured ChatModel (Ollama).
 * It exposes endpoints for both synchronous and streaming-based chat responses.
 *
 * NOTE: This controller demonstrates basic AI interaction. The core TaskMaster logic
 * should reside in dedicated services like TaskMasterService, TaskExecutionAgent etc.
 * The 'stopModel' call has been commented out as frequently stopping/starting the Ollama
 * process is generally inefficient and negates the benefits of having models loaded in memory.
 * Consider using StopModel only for specific administrative tasks or during application shutdown.
 */
@RestController
@RequestMapping("/api/v1/ai") // Use versioned API path
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatModel chatModel; // Inject the interface for flexibility
    private final StopModel stopModel; // Keep injection for potential future use

    /**
     * Constructor for injecting dependencies.
     *
     * @param chatModel the chat model used for generating AI responses (injected via Spring AI)
     * @param stopModel service potentially used for stopping the model post-execution
     */
    @Autowired
    public ChatController(ChatModel chatModel, StopModel stopModel) {
        this.chatModel = chatModel;
        this.stopModel = stopModel;
    }

    /**
     * Handles synchronous AI response generation.
     * Expects a JSON body like: {"message": "What is Java?"}
     *
     * @param request The chat request DTO containing the user's message.
     * @return A Mono containing the chat response DTO.
     */
    @PostMapping(value = "/generate", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ChatResponseDto> generate(@Valid @RequestBody ChatRequest request) {
        log.info("Received synchronous generation request: {}", request.getMessage());

        return Mono.fromCallable(() -> chatModel.call(request.getMessage()))
                .map(ChatResponseDto::new) // Use constructor reference
                .doOnSuccess(response -> log.info("Successfully generated synchronous response."))
                .doOnError(e -> log.error("Error during synchronous generation: {}", e.getMessage(), e))
                .doFinally(signalType -> {
                    log.debug("Synchronous request finished (Signal: {}).", signalType);
                    // stopModel.stopModel(); // Avoid calling frequently - inefficient.
                });
    }

    /**
     * Handles streaming AI response generation.
     * Returns a Flux stream of SSE (Server-Sent Events).
     * Expects a JSON body like: {"message": "Tell me a story."}
     *
     * @param request The chat request DTO containing the user's message.
     * @return a reactive stream (Flux) of ChatResponse chunks as Server-Sent Events.
     */
    @PostMapping(value = "/generateStream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatResponse> generateStream(@Valid @RequestBody ChatRequest request) {
        log.info("Received streaming generation request: {}", request.getMessage());

        Prompt prompt = new Prompt(new UserMessage(request.getMessage()));

        return this.chatModel.stream(prompt)
                .doOnSubscribe(subscription -> log.info("Streaming connection established."))
                .doOnNext(chunk -> log.trace("Streaming chunk received: {}", chunk.getResult() != null ? chunk.getResult()
                        .getOutput().getContent() : "Metadata chunk"))
                .doOnError(e -> log.error("Error during streaming generation: {}", e.getMessage(), e))
                .doFinally(signalType -> {
                    log.info("Streaming request finished (Signal: {}).", signalType);
                    // stopModel.stopModel(); // Avoid calling frequently - inefficient.
                });
    }
}