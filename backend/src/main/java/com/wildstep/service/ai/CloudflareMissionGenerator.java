package com.wildstep.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.dto.cloudflare.CloudflareAiRequest;
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
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
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
            log.error("Cloudflare Workers AI credentials missing (CLOUDFLARE_ACCOUNT_ID or CLOUDFLARE_API_TOKEN is blank)");
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
            log.info("Invoking Cloudflare Workers AI model '{}' (attempt {}/{})", model, attempt, MAX_RETRIES);

            // Step 1: Execute HTTP call and read raw JSON envelope as String
            String rawEnvelopeJson = executeInferenceRequest(aiRequest);

            // Step 2: Parse raw envelope into Jackson JsonNode (fail-fast on envelope deserialization errors)
            JsonNode rootNode = parseResponseEnvelope(rawEnvelopeJson);

            // Step 3: Validate Cloudflare API success flag
            validateCloudflareEnvelopeSuccess(rootNode);

            // Step 4: Extract result.response text
            String rawAiText = extractResponseText(rootNode);

            // Step 5: Extract and parse mission JSON from model output (retryable if model hallucinates or malforms JSON)
            try {
                String jsonContent = extractJsonContent(rawAiText);
                if (jsonContent == null || jsonContent.isBlank()) {
                    throw new MissionValidationException("Cloudflare Workers AI response contained no valid JSON structure.");
                }

                MissionResponseDto missionResponse = objectMapper.readValue(jsonContent, MissionResponseDto.class);
                if (missionResponse.getTasks() != null) {
                    for (int i = 0; i < missionResponse.getTasks().size(); i++) {
                        missionResponse.getTasks().get(i).setTaskOrder(i + 1);
                    }
                }
                return missionResponse;

            } catch (MissionValidationException ex) {
                log.warn("Attempt {} output validation error: {}", attempt, ex.getMessage());
                lastException = ex;
            } catch (JsonProcessingException ex) {
                log.warn("Attempt {} output contained invalid mission JSON: {}", attempt, ex.getMessage());
                lastException = ex;
            } catch (Exception ex) {
                log.warn("Attempt {} unexpected error while parsing mission output: {}", attempt, ex.getMessage());
                lastException = ex;
            }
        }

        throw new MissionValidationException(
                "Failed to generate a valid structured mission via Cloudflare Workers AI after " + MAX_RETRIES + " attempts. Error: " +
                (lastException != null ? lastException.getMessage() : "Unknown error")
        );
    }

    private String executeInferenceRequest(CloudflareAiRequest aiRequest) {
        String path = "/accounts/" + accountId + "/ai/run/" + model;

        try {
            org.springframework.http.ResponseEntity<String> response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(aiRequest)
                    .retrieve()
                    .toEntity(String.class);

            int status = response.getStatusCode().value();
            MediaType contentType = response.getHeaders().getContentType();
            log.info("Cloudflare Workers AI HTTP response received: status={}, contentType={}", status, contentType);

            return response.getBody();

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
        } catch (RestClientException ex) {
            log.error("Cloudflare Workers AI REST client error: {}", ex.getMessage());
            throw new AiServiceUnavailableException(
                    "Cloudflare Workers AI communication failed: " + ex.getMessage(),
                    ex
            );
        }
    }

    private JsonNode parseResponseEnvelope(String rawEnvelopeJson) {
        if (rawEnvelopeJson == null || rawEnvelopeJson.isBlank()) {
            log.error("Cloudflare Workers AI returned empty HTTP response body");
            throw new AiServiceUnavailableException("Cloudflare Workers AI returned an empty response body.");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawEnvelopeJson);
            logEnvelopeDiagnostics(rootNode);
            return rootNode;
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse Cloudflare Workers AI response envelope as JSON: {}", ex.getMessage());
            throw new AiServiceUnavailableException("Cloudflare Workers AI returned an invalid JSON response envelope.", ex);
        }
    }

    private void logEnvelopeDiagnostics(JsonNode rootNode) {
        if (rootNode == null) {
            return;
        }
        List<String> topLevelKeys = new ArrayList<>();
        rootNode.fieldNames().forEachRemaining(topLevelKeys::add);
        boolean isObject = rootNode.isObject();
        boolean success = rootNode.path("success").asBoolean(false);
        int errorsCount = rootNode.path("errors").isArray() ? rootNode.path("errors").size() : 0;
        int messagesCount = rootNode.path("messages").isArray() ? rootNode.path("messages").size() : 0;

        JsonNode resultNode = rootNode.path("result");
        String resultNodeType = resultNode.getNodeType().name();

        java.util.Map<String, String> childKeysAndTypes = new java.util.LinkedHashMap<>();
        if (resultNode.isObject()) {
            resultNode.fields().forEachRemaining(e -> childKeysAndTypes.put(e.getKey(), e.getValue().getNodeType().name()));
        }

        log.info("Cloudflare envelope diagnostics: isObject={}, topLevelKeys={}, success={}, errorsCount={}, messagesCount={}, resultNodeType={}, resultKeysAndTypes={}",
                isObject, topLevelKeys, success, errorsCount, messagesCount, resultNodeType, childKeysAndTypes);
    }

    private void validateCloudflareEnvelopeSuccess(JsonNode rootNode) {
        boolean success = rootNode.path("success").asBoolean(false);
        if (!success) {
            logEnvelopeDiagnostics(rootNode);
            String errorDetail = extractErrorDetails(rootNode);
            log.warn("Cloudflare Workers AI returned unsuccessful response envelope: {}", errorDetail);
            throw new AiServiceUnavailableException("Cloudflare Workers AI returned an error response: " + errorDetail);
        }
    }

    private String extractResponseText(JsonNode rootNode) {
        JsonNode resultNode = rootNode.path("result");

        // Case 1: result is missing or null
        if (resultNode.isMissingNode() || resultNode.isNull()) {
            log.error("Cloudflare response diagnosis: 'result' is missing or null (nodeType={})", resultNode.getNodeType());
            throw new AiServiceUnavailableException("Cloudflare Workers AI response missing 'result' field.");
        }

        // Case 2: result is a textual value instead of an object
        if (resultNode.isTextual()) {
            String text = resultNode.asText();
            log.info("Cloudflare response diagnosis: 'result' is a textual value instead of an object (length={})", text.length());
            if (text.isBlank()) {
                log.error("Cloudflare response diagnosis: 'result' text is blank");
                throw new AiServiceUnavailableException("Cloudflare Workers AI response contains empty 'result' text.");
            }
            return text;
        }

        // Ensure result is an object
        if (!resultNode.isObject()) {
            log.error("Cloudflare response diagnosis: 'result' is neither an object nor textual (nodeType={})", resultNode.getNodeType());
            throw new AiServiceUnavailableException("Cloudflare Workers AI response 'result' has unexpected type: " + resultNode.getNodeType());
        }

        java.util.Map<String, String> childKeys = new java.util.LinkedHashMap<>();
        resultNode.fields().forEachRemaining(e -> childKeys.put(e.getKey(), e.getValue().getNodeType().name()));

        // Case 3: result is an object but has no response field
        if (!resultNode.has("response")) {
            log.error("Cloudflare response diagnosis: 'result' is an object but has no 'response' field. Available fields: {}", childKeys);

            // Check if alternate documented text field 'text' is present
            if (resultNode.hasNonNull("text") && resultNode.get("text").isTextual() && !resultNode.get("text").asText().isBlank()) {
                log.info("Cloudflare response diagnosis: found alternate textual field 'result.text' (length={})", resultNode.get("text").asText().length());
                return resultNode.get("text").asText();
            }

            throw new AiServiceUnavailableException("Cloudflare Workers AI response missing 'result.response' content. Available fields: " + childKeys.keySet());
        }

        JsonNode responseNode = resultNode.get("response");

        // Case 4: result.response is null
        if (responseNode == null || responseNode.isNull()) {
            log.error("Cloudflare response diagnosis: 'result.response' is null");
            throw new AiServiceUnavailableException("Cloudflare Workers AI response missing 'result.response' content (null).");
        }

        // Case 5: result.response is present but not textual
        if (!responseNode.isTextual()) {
            log.error("Cloudflare response diagnosis: 'result.response' is present but not textual (nodeType={})", responseNode.getNodeType());
            if (responseNode.isObject()) {
                log.info("Cloudflare response diagnosis: 'result.response' is an ObjectNode; extracting JSON content");
                return responseNode.toString();
            }
            throw new AiServiceUnavailableException("Cloudflare Workers AI response missing 'result.response' content (non-textual type: " + responseNode.getNodeType() + ").");
        }

        // Case 6: result.response is a non-empty text value
        String rawResponseText = responseNode.asText();
        if (rawResponseText.isBlank()) {
            log.error("Cloudflare response diagnosis: 'result.response' is empty or blank text");
            throw new AiServiceUnavailableException("Cloudflare Workers AI response missing 'result.response' content (blank).");
        }

        log.info("Cloudflare response diagnosis: 'result.response' is a non-empty text value (length={})", rawResponseText.length());
        return rawResponseText;
    }

    static String extractErrorDetails(JsonNode rootNode) {
        List<String> details = new ArrayList<>();

        JsonNode errorsNode = rootNode.path("errors");
        if (errorsNode.isArray()) {
            for (JsonNode err : errorsNode) {
                if (err.isTextual()) {
                    details.add(err.asText());
                } else if (err.hasNonNull("message")) {
                    String codePrefix = err.hasNonNull("code") ? ("[" + err.get("code").asText() + "] ") : "";
                    details.add(codePrefix + err.get("message").asText());
                } else {
                    details.add(err.toString());
                }
            }
        }

        JsonNode messagesNode = rootNode.path("messages");
        if (messagesNode.isArray()) {
            for (JsonNode msg : messagesNode) {
                if (msg.isTextual()) {
                    details.add(msg.asText());
                } else if (msg.hasNonNull("message")) {
                    details.add(msg.get("message").asText());
                }
            }
        }

        return details.isEmpty() ? "Unknown upstream error" : String.join("; ", details);
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
