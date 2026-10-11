package com.touchgrass.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.touchgrass.dto.UserCreateRequestDto;
import com.touchgrass.dto.UserResponseDto;
import com.touchgrass.exception.ConflictException;
import com.touchgrass.exception.ResourceNotFoundException;
import com.touchgrass.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @Test
    @DisplayName("POST /api/users - Should create user when request is valid")
    void createUser_Success() throws Exception {
        UserCreateRequestDto requestDto = new UserCreateRequestDto("testuser", "test@example.com");

        UserResponseDto responseDto = new UserResponseDto();
        responseDto.setId(1L);
        responseDto.setUsername("testuser");
        responseDto.setEmail("test@example.com");
        responseDto.setTotalPoints(0);
        responseDto.setCreatedAt(LocalDateTime.now());

        Mockito.when(userService.createUser(any(UserCreateRequestDto.class))).thenReturn(responseDto);

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.username").value("testuser"))
                .andExpect(jsonPath("$.data.email").value("test@example.com"));
    }

    @Test
    @DisplayName("POST /api/users - Should return 400 Bad Request when validation fails")
    void createUser_ValidationError() throws Exception {
        UserCreateRequestDto requestDto = new UserCreateRequestDto("", "invalid-email");

        mockMvc.perform(post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Should return user when exists")
    void getUserById_Success() throws Exception {
        UserResponseDto responseDto = new UserResponseDto();
        responseDto.setId(1L);
        responseDto.setUsername("john");
        responseDto.setEmail("john@example.com");

        Mockito.when(userService.getUserById(1L)).thenReturn(responseDto);

        mockMvc.perform(get("/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("john"));
    }

    @Test
    @DisplayName("GET /api/users/{id} - Should return 404 Not Found when user does not exist")
    void getUserById_NotFound() throws Exception {
        Mockito.when(userService.getUserById(99L))
                .thenThrow(new ResourceNotFoundException("User with ID 99 not found."));

        mockMvc.perform(get("/users/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource Not Found"))
                .andExpect(jsonPath("$.status").value(404));
    }
}
