package com.challenge.AuthApi.controller;

import com.challenge.AuthApi.config.TestSecurityConfig;
import com.challenge.AuthApi.config.TestSecurityConfig;
import com.challenge.AuthApi.entity.User;
import com.challenge.AuthApi.exception.GlobalExceptionHandler;
import com.challenge.AuthApi.exception.UserAlreadyExistsException;
import com.challenge.AuthApi.security.JwtService;
import com.challenge.AuthApi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void register_valid_shouldReturn201() throws Exception {
        User saved = User.builder().id(1L).nome("Maria").email("maria@email.com").role("USER").build();
        when(userService.createUser(any(User.class))).thenReturn(saved);

        String body = """
                {"nome":"Maria","email":"maria@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Maria"))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void register_response_hasCamposExistentes() throws Exception {
        User saved = User.builder().id(2L).nome("Maria Silva").email("maria@email.com").role("USER").build();
        when(userService.createUser(any(User.class))).thenReturn(saved);

        String body = """
                {"nome":"Maria Silva","email":"maria@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.nome").value("Maria Silva"))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.role").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());
    }

    @Test
    void register_response_valoresCorretos_protecaoRegressao() throws Exception {
        User saved = User.builder().id(10L).nome("Joao Santos").email("joao@email.com").role("USER").build();
        when(userService.createUser(any(User.class))).thenReturn(saved);

        String body = """
                {"nome":"Joao Santos","email":"joao@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.nome").value("Joao Santos"))
                .andExpect(jsonPath("$.email").value("joao@email.com"))
                .andExpect(jsonPath("$.nome").value(org.hamcrest.Matchers.not("joao@email.com")))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.not("USER")))
                .andExpect(jsonPath("$.email").value(org.hamcrest.Matchers.not("Joao Santos")));
    }

    @Test
    void register_invalid_shouldReturn400() throws Exception {
        String body = """
                {"nome":"","email":"invalido","senha":"123"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_duplicate_shouldReturn409() throws Exception {
        when(userService.createUser(any(User.class)))
                .thenThrow(new UserAlreadyExistsException("User already exists with this email"));

        String body = """
                {"nome":"Maria","email":"dup@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void register_ignoresRoleSentByClient_shouldDefaultToUser() throws Exception {
        User saved = User.builder().id(3L).nome("Maria").email("maria3@email.com").role("USER").build();
        when(userService.createUser(any(User.class))).thenReturn(saved);

        String body = """
                {"nome":"Maria","email":"maria3@email.com","senha":"senha123","role":"ADMIN"}""";

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void login_valid_shouldReturn200WithTokenEmailRole() throws Exception {
        User user = User.builder().id(1L).email("maria@email.com").role("USER").build();
        when(userService.authenticate(eq("maria@email.com"), eq("senha123"))).thenReturn(user);
        when(jwtService.generateToken("maria@email.com", "USER")).thenReturn("jwt-token");

        String body = """
                {"email":"maria@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.nome").doesNotExist());
    }

    @Test
    void login_valid_withAdmin_shouldReturnRoleAdmin() throws Exception {
        User user = User.builder().id(2L).email("admin@email.com").role("ADMIN").build();
        when(userService.authenticate(eq("admin@email.com"), eq("admin123"))).thenReturn(user);
        when(jwtService.generateToken("admin@email.com", "ADMIN")).thenReturn("jwt-admin-token");

        String body = """
                {"email":"admin@email.com","senha":"admin123"}""";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-admin-token"))
                .andExpect(jsonPath("$.email").value("admin@email.com"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.nome").doesNotExist());
    }

    @Test
    void login_invalidPassword_shouldReturn401() throws Exception {
        when(userService.authenticate(anyString(), anyString()))
                .thenThrow(new BadCredentialsException("Invalid password"));

        String body = """
                {"email":"maria@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_userNotFound_shouldReturn401() throws Exception {
        when(userService.authenticate(anyString(), anyString()))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "User not found"));

        String body = """
                {"email":"ghost@email.com","senha":"senha123"}""";

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validate_validToken_shouldReturn200WithValidTrue() throws Exception {
        User user = User.builder().id(1L).email("maria@email.com").role("USER").build();
        when(userService.validateToken(eq("valid.token"), any())).thenReturn(user);

        String body = """
                {"token":"valid.token"}""";

        mockMvc.perform(post("/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.email").value("maria@email.com"))
                .andExpect(jsonPath("$.role").value("USER"));
    }

    @Test
    void validate_invalidToken_shouldReturn401() throws Exception {
        when(userService.validateToken(anyString(), any()))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Token inválido"));

        String body = """
                {"token":"bad.token"}""";

        mockMvc.perform(post("/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void validate_expiredToken_shouldReturn401() throws Exception {
        when(userService.validateToken(anyString(), any()))
                .thenThrow(new ResponseStatusException(UNAUTHORIZED, "Token inválido"));

        String body = """
                {"token":"expired.token.xyz"}""";

        mockMvc.perform(post("/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}