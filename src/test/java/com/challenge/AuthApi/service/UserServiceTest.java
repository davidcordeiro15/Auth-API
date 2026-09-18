package com.challenge.AuthApi.service;

import com.challenge.AuthApi.entity.User;
import com.challenge.AuthApi.exception.UserAlreadyExistsException;
import com.challenge.AuthApi.exception.UserNotFoundException;
import com.challenge.AuthApi.repository.UserRepository;
import com.challenge.AuthApi.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @Test
    void createUser_valid_shouldCreateAndEncodePassword() {
        User input = User.builder().nome("Maria").email("maria@email.com").senha("senha123").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("encoded");
        User saved = User.builder().id(1L).nome("Maria").email("maria@email.com").senha("encoded").role("USER").build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        User result = userService.createUser(input);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals("USER", result.getRole());
        verify(passwordEncoder).encode("senha123");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void createUser_passwordIsEncoded_beforeSave() {
        User input = User.builder().nome("Maria").email("maria@email.com").senha("senha123").build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(input);

        assertEquals("hashed", result.getSenha());
        assertEquals("USER", result.getRole());
    }

    @Test
    void createUser_shouldBeSaved_withEncodedPassword() {
        User input = User.builder().nome("Maria").email("maria@email.com").senha("senha123").build();
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("enc");
        when(userRepository.save(any(User.class))).thenReturn(input);

        userService.createUser(input);

        verify(userRepository).save(argThat(u -> "enc".equals(u.getSenha())));
    }

    @Test
    void createUser_existingEmail_shouldThrowAlreadyExists() {
        User input = User.builder().nome("Maria").email("dup@email.com").senha("senha123").build();
        when(userRepository.findByEmail("dup@email.com")).thenReturn(Optional.of(new User()));

        assertThrows(UserAlreadyExistsException.class, () -> userService.createUser(input));
        verify(userRepository, never()).save(any());
    }

    @Test
    void createUser_withoutRole_shouldDefaultToUser() {
        User input = User.builder().nome("Maria").email("maria@email.com").senha("senha123").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(input);

        assertEquals("USER", result.getRole());
    }

    @Test
    void createUser_withExplicitRole_shouldBeOverriddenToUser() {
        User input = User.builder().nome("Maria").email("maria@email.com").senha("senha123").role("ADMIN").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("senha123")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.createUser(input);

        assertEquals("USER", result.getRole());
    }

    @Test
    void authenticate_valid_shouldReturnUser() {
        User stored = User.builder().id(1L).email("maria@email.com").senha("hashed").role("USER").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("senha123", "hashed")).thenReturn(true);

        User result = userService.authenticate("maria@email.com", "senha123");

        assertEquals("maria@email.com", result.getEmail());
    }

    @Test
    void authenticate_wrongPassword_shouldThrowBadCredentials() {
        User stored = User.builder().email("maria@email.com").senha("hashed").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> userService.authenticate("maria@email.com", "wrong"));
    }

    @Test
    void authenticate_nonExistentUser_shouldThrowUsernameNotFound() {
        when(userRepository.findByEmail("no@email.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> userService.authenticate("no@email.com", "senha123"));
    }

    @Test
    void validateToken_validToken_shouldReturnUser() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.isValid("valid.token")).thenReturn(true);
        when(jwtService.extractEmail("valid.token")).thenReturn("maria@email.com");
        User stored = User.builder().id(1L).email("maria@email.com").role("USER").build();
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(stored));

        User result = userService.validateToken("valid.token", jwtService);

        assertEquals("maria@email.com", result.getEmail());
    }

    @Test
    void validateToken_invalidToken_shouldThrowUnauthorized() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.isValid("bad.token")).thenReturn(false);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.validateToken("bad.token", jwtService));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Token inválido", ex.getReason());
    }

    @Test
    void validateToken_userNotFound_shouldThrowUnauthorized() {
        JwtService jwtService = mock(JwtService.class);
        when(jwtService.isValid("valid.token")).thenReturn(true);
        when(jwtService.extractEmail("valid.token")).thenReturn("ghost@email.com");
        when(userRepository.findByEmail("ghost@email.com")).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.validateToken("valid.token", jwtService));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Usuário não encontrado", ex.getReason());
    }
}