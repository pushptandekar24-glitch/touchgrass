package com.touchgrass.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.touchgrass.domain.enums.Difficulty;
import com.touchgrass.domain.enums.EnvironmentType;
import com.touchgrass.domain.enums.Mood;
import com.touchgrass.domain.enums.Participants;
import com.touchgrass.dto.MissionGenerateRequestDto;
import com.touchgrass.dto.MissionResponseDto;
import com.touchgrass.exception.OllamaUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OllamaMissionGeneratorTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer mockServer;
    private ObjectMapper objectMapper;
    private OllamaMissionGenerator generator;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder().baseUrl("http://localhost:11434");
        mockServer = MockRestServiceServer.bindTo(restClientBuilder).build();
        objectMapper = new ObjectMapper();
        generator = new OllamaMissionGenerator(restClientBuilder.build(), objectMapper);
    }

    @Test
    @DisplayName("Should successfully parse Ollama JSON response into MissionResponseDto")
    void generateMission_Success() throws Exception {
        String ollamaJsonResponseBody = """
            {
              "model": "gemma2:2b",
              "done": true,
              "message": {
                "role": "assistant",
                "content": "{\\"title\\":\\"20-Minute Campus Reset\\",\\"description\\":\\"A short outdoors reset walk.\\",\\"tasks\\":[{\\"description\\":\\"Walk around lawn\\",\\"estimatedMinutes\\":8},{\\"description\\":\\"Sit on bench\\",\\"estimatedMinutes\\":7}],\\"bonusTask\\":\\"Notice one detail.\\"}"
              }
            }
            """;

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess(ollamaJsonResponseBody, MediaType.APPLICATION_JSON));

        MissionGenerateRequestDto requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        MissionResponseDto result = generator.generateMission(requestDto);

        assertNotNull(result);
        assertEquals("20-Minute Campus Reset", result.getTitle());
        assertEquals(2, result.getTasks().size());
        assertEquals("Walk around lawn", result.getTasks().get(0).getDescription());
        assertEquals(1, result.getTasks().get(0).getTaskOrder());

        mockServer.verify();
    }

    @Test
    @DisplayName("Should throw OllamaUnavailableException when Ollama HTTP service is unavailable")
    void generateMission_OllamaUnavailable() {
        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        MissionGenerateRequestDto requestDto = new MissionGenerateRequestDto(
                20, Mood.STRESSED, "Relax", EnvironmentType.CAMPUS, Difficulty.EASY, Participants.SOLO
        );

        assertThrows(
                OllamaUnavailableException.class,
                () -> generator.generateMission(requestDto)
        );

        mockServer.verify();
    }
}
