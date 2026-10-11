package com.wildstep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.MissionTaskResponseDto;
import com.wildstep.dto.ollama.OllamaChatRequest;
import com.wildstep.dto.ollama.OllamaChatResponse;
import com.wildstep.exception.MissionValidationException;
import com.wildstep.exception.OllamaUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;

@Service
@ConditionalOnProperty(name = "wildstep.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaMissionGenerator implements MissionGenerator {

    private static final Logger log = LoggerFactory.getLogger(OllamaMissionGenerator.class);
    private static final int MAX_RETRIES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${ollama.model:gemma2:2b}")
    private String model;

    public OllamaMissionGenerator(RestClient ollamaRestClient, ObjectMapper objectMapper) {
        this.restClient = ollamaRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MissionResponseDto generateMission(MissionGenerateRequestDto request) {
        String systemPrompt = buildSystemPrompt(request);
        String userPrompt = String.format(
                "Generate a %d-minute outdoor mission for a user in a %s environment. Mood: %s. Goal: %s. Difficulty: %s. Group: %s.",
                request.getAvailableMinutes(),
                request.getEnvironmentType().name(),
                request.getMood().name(),
                request.getGoal(),
                request.getDifficulty().name(),
                request.getParticipants().name()
        );

        Map<String, Object> jsonSchemaFormat = buildJsonSchemaFormat();

        OllamaChatRequest chatRequest = new OllamaChatRequest(
                model,
                List.of(
                        new OllamaChatRequest.OllamaMessage("system", systemPrompt),
                        new OllamaChatRequest.OllamaMessage("user", userPrompt)
                ),
                jsonSchemaFormat,
                Map.of("temperature", 0.2, "top_p", 0.9)
        );

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                log.info("Invoking Ollama model '{}' (attempt {}/{})", model, attempt, MAX_RETRIES);
                OllamaChatResponse response = restClient.post()
                        .uri("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(chatRequest)
                        .retrieve()
                        .body(OllamaChatResponse.class);

                if (response == null || response.getMessage() == null || response.getMessage().getContent() == null) {
                    throw new MissionValidationException("Ollama returned an empty response body.");
                }

                String rawContent = response.getMessage().getContent();
                log.debug("Ollama raw JSON output: {}", rawContent);

                MissionResponseDto missionResponse = objectMapper.readValue(rawContent, MissionResponseDto.class);
                if (missionResponse.getTasks() != null) {
                    for (int i = 0; i < missionResponse.getTasks().size(); i++) {
                        missionResponse.getTasks().get(i).setTaskOrder(i + 1);
                    }
                }
                return missionResponse;

            } catch (RestClientException ex) {
                log.error("Failed to connect to local Ollama service: {}", ex.getMessage());
                throw new OllamaUnavailableException("Ollama service is unavailable at http://localhost:11434. Ensure Ollama is running.", ex);
            } catch (Exception ex) {
                log.warn("Attempt {} failed to parse mission JSON output: {}", attempt, ex.getMessage());
                lastException = ex;
            }
        }

        throw new MissionValidationException("Failed to generate a valid structured mission after " + MAX_RETRIES + " attempts. Error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error"));
    }

    private String buildSystemPrompt(MissionGenerateRequestDto request) {
        return "You are WildStep, an expert outdoor activity planner.\n" +
               "Generate one safe, realistic, personalized outdoor mission.\n" +
               "SAFETY CONSTRAINTS:\n" +
               "- Do NOT encourage trespassing, restricted areas, dangerous climbing, railroad tracks, highway traffic, or approaching strangers.\n" +
               "- Do NOT invent non-existent nearby facilities.\n" +
               "- Stay strictly within safe public areas adaptable to the user's environment.\n" +
               "FORMAT RULES:\n" +
               "- Return strict JSON with fields: 'title', 'description', 'tasks' (array of objects with 'description' and 'estimatedMinutes'), and 'bonusTask'.\n" +
               "- The sum of task 'estimatedMinutes' MUST be less than or equal to " + request.getAvailableMinutes() + " minutes.\n" +
               "- Generate between 2 and 5 tasks.";
    }

    private Map<String, Object> buildJsonSchemaFormat() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "title", Map.of("type", "string"),
                        "description", Map.of("type", "string"),
                        "tasks", Map.of(
                                "type", "array",
                                "items", Map.of(
                                        "type", "object",
                                        "properties", Map.of(
                                                "description", Map.of("type", "string"),
                                                "estimatedMinutes", Map.of("type", "integer")
                                        ),
                                        "required", List.of("description", "estimatedMinutes")
                                )
                        ),
                        "bonusTask", Map.of("type", "string")
                ),
                "required", List.of("title", "description", "tasks")
        );
    }
}
