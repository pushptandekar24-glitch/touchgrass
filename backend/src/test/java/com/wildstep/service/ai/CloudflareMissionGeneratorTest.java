package com.wildstep.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wildstep.domain.enums.Difficulty;
import com.wildstep.domain.enums.EnvironmentType;
import com.wildstep.domain.enums.Mood;
import com.wildstep.domain.enums.Participants;
import com.wildstep.dto.MissionGenerateRequestDto;
import com.wildstep.dto.MissionResponseDto;
import com.wildstep.exception.AiServiceUnavailableException;
import com.wildstep.exception.MissionValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class CloudflareMissionGeneratorTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private CloudflareMissionGenerator generator;

    private static final String ACCOUNT_ID = "test-account-id";
    private static final String API_TOKEN = "test-api-token";
    private static final String MODEL = "@cf/meta/llama-3.2-3b-instruct";
    private static final String EXPECTED_URL = "https://api.cloudflare.com/client/v4/accounts/" + ACCOUNT_ID + "/ai/run/" + MODEL;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("https://api.cloudflare.com/client/v4");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
        generator = new CloudflareMissionGenerator(restClientBuilder.build(), objectMapper);

        ReflectionTestUtils.setField(generator, "accountId", ACCOUNT_ID);
        ReflectionTestUtils.setField(generator, "apiToken", API_TOKEN);
        ReflectionTestUtils.setField(generator, "model", MODEL);
    }

    private MissionGenerateRequestDto createSampleRequest() {
        return new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax outside", EnvironmentType.PARK, Difficulty.EASY, Participants.SOLO
        );
    }

    @Test
    @DisplayName("Should successfully parse Cloudflare raw JSON response into MissionResponseDto")
    void generateMission_Success() {
        String cloudflareResponseBody = """
            {
              "result": {
                "response": "{\\"title\\":\\"Park Meadow Stroll\\",\\"description\\":\\"Ground yourself in the open lawn.\\",\\"tasks\\":[{\\"description\\":\\"Walk around perimeter\\",\\"estimatedMinutes\\":10},{\\"description\\":\\"Sit under large tree\\",\\"estimatedMinutes\\":8}],\\"bonusTask\\":\\"Identify two songbirds.\\"}"
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(cloudflareResponseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Park Meadow Stroll", result.getTitle());
        assertEquals(2, result.getTasks().size());
        assertEquals("Walk around perimeter", result.getTasks().get(0).getDescription());
        assertEquals(1, result.getTasks().get(0).getTaskOrder());
        assertEquals(2, result.getTasks().get(1).getTaskOrder());

        mockServer.verify();
    }

    @Test
    @DisplayName("Should parse Cloudflare response wrapped in markdown code fence")
    void generateMission_MarkdownWrappedResponse() {
        String cloudflareResponseBody = """
            {
              "result": {
                "response": "```json\\n{\\"title\\":\\"Balcony Breeze\\",\\"description\\":\\"Take in fresh air.\\",\\"tasks\\":[{\\"description\\":\\"Deep breathing outside\\",\\"estimatedMinutes\\":5}],\\"bonusTask\\":null}\\n```"
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(cloudflareResponseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Balcony Breeze", result.getTitle());
        assertEquals(1, result.getTasks().size());

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw AiServiceUnavailableException if Cloudflare credentials are missing")
    void generateMission_MissingCredentials() {
        ReflectionTestUtils.setField(generator, "apiToken", "");

        assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );
    }

    @Test
    @DisplayName("Should map HTTP 429 rate limit to AiServiceUnavailableException with rate limit guidance")
    void generateMission_RateLimit_429() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("rate limit"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should successfully handle Cloudflare response envelope containing structured message objects")
    void generateMission_StructuredMessagesObject_Success() {
        String cloudflareResponseBody = """
            {
              "result": {
                "response": "{\\"title\\":\\"Park Meadow Stroll\\",\\"description\\":\\"Ground yourself in the open lawn.\\",\\"tasks\\":[{\\"description\\":\\"Walk around perimeter\\",\\"estimatedMinutes\\":10}],\\"bonusTask\\":null}"
              },
              "success": true,
              "errors": [],
              "messages": [
                {
                  "code": 10001,
                  "message": "Model execution note or billing notice"
                }
              ]
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_TOKEN))
                .andRespond(withSuccess(cloudflareResponseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Park Meadow Stroll", result.getTitle());
        assertEquals(1, result.getTasks().size());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fail fast on Cloudflare error envelope without retrying")
    void generateMission_CloudflareErrorEnvelope() {
        String errorResponseBody = """
            {
              "result": null,
              "success": false,
              "errors": [
                {
                  "code": 7003,
                  "message": "Could not route to that model"
                }
              ],
              "messages": []
            }
            """;

        // Expect exactly 1 request (no retry for upstream API failure)
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(errorResponseBody, MediaType.APPLICATION_JSON));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("Could not route to that model"));
        assertTrue(ex.getMessage().contains("7003"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fail fast when result object is null or missing")
    void generateMission_MissingResultObject() {
        String responseBody = """
            {
              "result": null,
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("missing 'result' field"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should fail fast when result.response is null or missing")
    void generateMission_NullResultResponse() {
        String responseBody = """
            {
              "result": {
                "response": null
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("missing 'result.response' content"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 401 authentication error to AiServiceUnavailableException")
    void generateMission_AuthFailed_401() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("authentication failed"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should map HTTP 403 forbidden error to AiServiceUnavailableException")
    void generateMission_AuthFailed_403() {
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("authentication failed"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should retry when initial attempt outputs malformed JSON and succeed on retry")
    void generateMission_RetrySuccess() {
        String badResponseBody = """
            {
              "result": {
                "response": "Here is your mission: not a valid json"
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        String goodResponseBody = """
            {
              "result": {
                "response": "{\\"title\\":\\"Garden Walk\\",\\"description\\":\\"Walk outdoors.\\",\\"tasks\\":[{\\"description\\":\\"Touch one leaf\\",\\"estimatedMinutes\\":5}],\\"bonusTask\\":null}"
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(badResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(goodResponseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Garden Walk", result.getTitle());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should exhaust retries and throw MissionValidationException when model output is consistently invalid JSON")
    void generateMission_InvalidMissionJson_ExhaustsRetries() {
        String badResponseBody = """
            {
              "result": {
                "response": "I cannot fulfill this request right now."
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        // MAX_RETRIES = 2
        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(badResponseBody, MediaType.APPLICATION_JSON));

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(badResponseBody, MediaType.APPLICATION_JSON));

        MissionValidationException ex = assertThrows(
                MissionValidationException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("Failed to generate a valid structured mission via Cloudflare Workers AI after 2 attempts"));
        mockServer.verify();
    }

    @Test
    @DisplayName("extractJsonContent utility correctly extracts raw JSON from various formats")
    void extractJsonContent_Formats() {
        assertEquals("{\"test\":1}", CloudflareMissionGenerator.extractJsonContent("{\"test\":1}"));
        assertEquals("{\"test\":2}", CloudflareMissionGenerator.extractJsonContent("```json\n{\"test\":2}\n```"));
        assertEquals("{\"test\":3}", CloudflareMissionGenerator.extractJsonContent("Here is your JSON:\n{\"test\":3}\nHope you enjoy!"));
        assertNull(CloudflareMissionGenerator.extractJsonContent(null));
    }

    @Test
    @DisplayName("CloudflareAiResponse DTO deserializes structured error and message objects correctly")
    void cloudflareAiResponseDto_Deserialization() throws Exception {
        String json = """
            {
              "result": {
                "response": "test output"
              },
              "success": true,
              "errors": [
                {"code": 1000, "message": "Error message"}
              ],
              "messages": [
                {"code": 2000, "message": "Notice message"}
              ]
            }
            """;

        com.wildstep.dto.cloudflare.CloudflareAiResponse dto = objectMapper.readValue(
                json, com.wildstep.dto.cloudflare.CloudflareAiResponse.class
        );

        assertNotNull(dto);
        assertTrue(dto.isSuccess());
        assertEquals("test output", dto.getResult().getResponse());
        assertEquals(1, dto.getErrors().size());
        assertEquals(1000, dto.getErrors().get(0).getCode());
        assertEquals("Error message", dto.getErrors().get(0).getMessage());
        assertEquals(1, dto.getMessages().size());
        assertEquals(2000, dto.getMessages().get(0).getCode());
        assertEquals("Notice message", dto.getMessages().get(0).getMessage());
    }

    @Test
    @DisplayName("Should parse Cloudflare response where result is a direct scalar string")
    void generateMission_ScalarStringResult_Success() {
        String responseBody = """
            {
              "result": "{\\"title\\":\\"Scalar Stroll\\",\\"description\\":\\"Direct scalar string result.\\",\\"tasks\\":[{\\"description\\":\\"Walk outdoors\\",\\"estimatedMinutes\\":5}],\\"bonusTask\\":null}",
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Scalar Stroll", result.getTitle());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should parse Cloudflare response using alternate 'text' field inside result")
    void generateMission_AlternateTextField_Success() {
        String responseBody = """
            {
              "result": {
                "text": "{\\"title\\":\\"Alternate Text\\",\\"description\\":\\"Extracted via text property.\\",\\"tasks\\":[{\\"description\\":\\"Breathe fresh air\\",\\"estimatedMinutes\\":5}],\\"bonusTask\\":null}"
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Alternate Text", result.getTitle());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should parse Cloudflare response when result.response is a structured JSON ObjectNode")
    void generateMission_ObjectNodeResponse_Success() {
        String responseBody = """
            {
              "result": {
                "response": {
                  "title": "Structured Object Mission",
                  "description": "JSON object node response.",
                  "tasks": [
                    {"description": "Sit on bench", "estimatedMinutes": 8}
                  ],
                  "bonusTask": null
                }
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        MissionResponseDto result = generator.generateMission(createSampleRequest());

        assertNotNull(result);
        assertEquals("Structured Object Mission", result.getTitle());
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw AiServiceUnavailableException when result.response is blank text")
    void generateMission_BlankResponseText_Throws() {
        String responseBody = """
            {
              "result": {
                "response": "   "
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("missing 'result.response' content"));
        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw AiServiceUnavailableException when result object has no recognized output field")
    void generateMission_ResultObjectNoResponseField_Throws() {
        String responseBody = """
            {
              "result": {
                "unknownField": 123
              },
              "success": true,
              "errors": [],
              "messages": []
            }
            """;

        mockServer.expect(requestTo(EXPECTED_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        AiServiceUnavailableException ex = assertThrows(
                AiServiceUnavailableException.class,
                () -> generator.generateMission(createSampleRequest())
        );

        assertTrue(ex.getMessage().contains("missing 'result.response' content"));
        mockServer.verify();
    }
}
