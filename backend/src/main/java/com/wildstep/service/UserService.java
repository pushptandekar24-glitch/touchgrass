package com.wildstep.service;

import com.wildstep.domain.User;
import com.wildstep.dto.UserCreateRequestDto;
import com.wildstep.dto.UserResponseDto;
import com.wildstep.exception.ConflictException;
import com.wildstep.exception.ResourceNotFoundException;
import com.wildstep.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponseDto createUser(UserCreateRequestDto requestDto) {
        if (userRepository.existsByUsername(requestDto.getUsername())) {
            throw new ConflictException("Username '" + requestDto.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            throw new ConflictException("Email '" + requestDto.getEmail() + "' is already registered.");
        }

        User user = new User(requestDto.getUsername(), requestDto.getEmail());
        User savedUser = userRepository.save(user);
        return UserResponseDto.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with ID " + id + " not found."));
        return UserResponseDto.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public UserResponseDto getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User with username '" + username + "' not found."));
        return UserResponseDto.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public List<UserResponseDto> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(UserResponseDto::fromEntity)
                .collect(Collectors.toList());
    }
}
