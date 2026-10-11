package com.wildstep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.cloudflare.CloudflareAiRequest;
import com.wildstep.dto.cloudflare.CloudflareAiResponse;
import com.wildstep.exception.AiServiceUnavailableException;
import com.wildstep.exception.MissionValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@ConditionalOnProperty(name = "wildstep.ai.provider", havingValue = "cloudflare")
public class CloudflareMissionGenerator implements MissionGenerator {

    private static final Logger log = LoggerFactory.getLogger(CloudflareMissionGenerator.class);
    private static final int MAX_RETRIES = 2;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${cloudflare.account-id:}")
    private String accountId;

    @Value("${cloudflare.api-token:}")
    private String apiToken;

    @Value("${cloudflare.model:@cf/meta/llama-3.2-3b-instruct}")
    private String model;

    public CloudflareMissionGenerator(RestClient cloudflareRestClient, ObjectMapper objectMapper) {
        this.restClient = cloudflareRestClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public MissionResponseDto generateMission(MissionGenerateRequestDto request) {
        if (accountId == null || accountId.isBlank() || apiToken == null || apiToken.isBlank()) {
            log.error("Cloudflare Workers AI credentials missing (CLOUDFLARE_ACCOUNT_ID or CLOUDFLARE_API_TOKEN is empty)");
            throw new AiServiceUnavailableException(
                    "Cloudflare Workers AI credentials not configured on the server. Set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN."
            );
        }

        String systemPrompt = buildSystemPrompt(request);
        String userPrompt = String.format(
                "Generate a %d-minute outdoor mission for a user in a %s environment. Mood: %s. Goal: %s. Difficulty: %s. Group: %s. Return ONLY valid JSON.",
                request.getAvailableMinutes(),
                request.getEnvironmentType().name(),
                request.getMood().name(),
                request.getGoal(),
                request.getDifficulty().name(),
                request.getParticipants().name()
        );

        CloudflareAiRequest aiRequest = new CloudflareAiRequest(
                List.of(
                        new CloudflareAiRequest.Message("system", systemPrompt),
                        new CloudflareAiRequest.Message("user", userPrompt)
                ),
                0.2,
                1024
        );

        int attempt = 0;
        Exception lastException = null;

        while (attempt < MAX_RETRIES) {
            attempt++;
            try {
                log.info("Invoking Cloudflare Workers AI model '{}' (attempt {}/{})", model, attempt, MAX_RETRIES);

                String path = "/accounts/" + accountId + "/ai/run/" + model;

                CloudflareAiResponse response = restClient.post()
                        .uri(path)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(aiRequest)
                        .retrieve()
                        .body(CloudflareAiResponse.class);

                if (response == null || !response.isSuccess() || response.getResult() == null || response.getResult().getResponse() == null) {
                    String errorMsg = (response != null && response.getErrors() != null && !response.getErrors().isEmpty())
                            ? response.getErrors().get(0).getMessage()
                            : "Empty response body";
                    throw new MissionValidationException("Cloudflare Workers AI returned unsuccessful response: " + errorMsg);
                }

                String rawResponse = response.getResult().getResponse();
                log.debug("Cloudflare raw AI output: {}", rawResponse);

                String jsonContent = extractJsonContent(rawResponse);
                if (jsonContent == null || jsonContent.isBlank()) {
                    throw new MissionValidationException("Cloudflare Workers AI output contained no valid JSON structure.");
                }

                MissionResponseDto missionResponse = objectMapper.readValue(jsonContent, MissionResponseDto.class);
                if (missionResponse.getTasks() != null) {
                    for (int i = 0; i < missionResponse.getTasks().size(); i++) {
                        missionResponse.getTasks().get(i).setTaskOrder(i + 1);
                    }
                }
                return missionResponse;

            } catch (HttpStatusCodeException ex) {
                int status = ex.getStatusCode().value();
                log.error("Cloudflare Workers AI HTTP error status {}", status);
                if (status == 429) {
                    throw new AiServiceUnavailableException(
                            "Cloudflare Workers AI daily rate limit or free tier quota exceeded (HTTP 429). Please try again later.",
                            ex
                    );
                } else if (status == 401 || status == 403) {
                    throw new AiServiceUnavailableException(
                            "Cloudflare Workers AI authentication failed (HTTP " + status + "). Please verify CLOUDFLARE_API_TOKEN.",
                            ex
                    );
                } else {
                    throw new AiServiceUnavailableException(
                            "Cloudflare Workers AI request failed with HTTP " + status + ": " + ex.getStatusText(),
                            ex
                    );
                }
            } catch (ResourceAccessException ex) {
                log.error("Cloudflare Workers AI connection timed out or network error: {}", ex.getMessage());
                throw new AiServiceUnavailableException(
                        "Cloudflare Workers AI connection timed out or is unreachable. Please check network connectivity.",
                        ex
                );
            } catch (MissionValidationException ex) {
                log.warn("Attempt {} validation error: {}", attempt, ex.getMessage());
                lastException = ex;
            } catch (Exception ex) {
                log.warn("Attempt {} failed to parse mission JSON output: {}", attempt, ex.getMessage());
                lastException = ex;
            }
        }

        throw new MissionValidationException(
                "Failed to generate a valid structured mission via Cloudflare Workers AI after " + MAX_RETRIES + " attempts. Error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error")
        );
    }

    static String extractJsonContent(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();

        // Strip leading ```json or ``` markdown wrapper
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.length() - 3);
            }
        }

        // Extract JSON object bounds if model added conversational preamble/postscript
        int firstBrace = trimmed.indexOf('{');
        int lastBrace = trimmed.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
            trimmed = trimmed.substring(firstBrace, lastBrace + 1);
        }

        return trimmed.trim();
    }

    private String buildSystemPrompt(MissionGenerateRequestDto request) {
        return "You are WildStep, an expert outdoor activity planner.\n" +
               "Generate one safe, realistic, personalized outdoor mission.\n" +
               "SAFETY CONSTRAINTS:\n" +
               "- Do NOT encourage trespassing, restricted areas, dangerous climbing, railroad tracks, highway traffic, or approaching strangers.\n" +
               "- Do NOT invent non-existent nearby facilities.\n" +
               "- Stay strictly within safe public areas adaptable to the user's environment.\n" +
               "OUTPUT FORMAT:\n" +
               "- Return strict JSON ONLY with NO surrounding commentary. Schema:\n" +
               "{\n" +
               "  \"title\": \"string\",\n" +
               "  \"description\": \"string\",\n" +
               "  \"tasks\": [\n" +
               "    {\"description\": \"string\", \"estimatedMinutes\": integer}\n" +
               "  ],\n" +
               "  \"bonusTask\": \"string\"\n" +
               "}\n" +
               "- The sum of task 'estimatedMinutes' MUST be less than or equal to " + request.getAvailableMinutes() + " minutes.\n" +
               "- Generate between 2 and 5 tasks.";
    }
}
