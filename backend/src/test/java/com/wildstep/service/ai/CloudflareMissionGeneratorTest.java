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
    @DisplayName("extractJsonContent utility correctly extracts raw JSON from various formats")
    void extractJsonContent_Formats() {
        assertEquals("{\"test\":1}", CloudflareMissionGenerator.extractJsonContent("{\"test\":1}"));
        assertEquals("{\"test\":2}", CloudflareMissionGenerator.extractJsonContent("```json\n{\"test\":2}\n```"));
        assertEquals("{\"test\":3}", CloudflareMissionGenerator.extractJsonContent("Here is your JSON:\n{\"test\":3}\nHope you enjoy!"));
        assertNull(CloudflareMissionGenerator.extractJsonContent(null));
    }
}
