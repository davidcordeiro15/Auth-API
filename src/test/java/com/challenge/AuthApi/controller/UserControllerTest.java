package com.challenge.AuthApi.controller;

import com.challenge.AuthApi.entity.User;
import com.challenge.AuthApi.exception.GlobalExceptionHandler;
import com.challenge.AuthApi.exception.UserAlreadyExistsException;
import com.challenge.AuthApi.exception.UserNotFoundException;
import com.challenge.AuthApi.config.TestExceptionHandler;
import com.challenge.AuthApi.config.TestSecurityConfig;
import com.challenge.AuthApi.security.JwtService;
import com.challenge.AuthApi.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = UserController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class, TestExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void list_authenticatedAsAdmin_shouldReturn200() throws Exception {
        User u = User.builder().id(1L).nome("Maria").email("maria@email.com").role("ADMIN").build();
        when(userService.findAll()).thenReturn(List.of(u));

        mockMvc.perform(get("/users")
                        .with(user("admin@email.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].nome").value("Maria"))
                .andExpect(jsonPath("$[0].email").value("maria@email.com"))
                .andExpect(jsonPath("$[0].senha").doesNotExist())
                .andExpect(jsonPath("$[0].password").doesNotExist())
                .andExpect(jsonPath("$[0].senhaHash").doesNotExist())
                .andExpect(jsonPath("$[0].role").doesNotExist());
    }

    @Test
    void update_asAdmin_shouldReturn200() throws Exception {
        User existing = User.builder().id(1L).nome("Updated").email("new@email.com").senha("hashed").role("USER").build();
        when(userService.update(eq(1L), any(User.class))).thenReturn(existing);

        String body = """
                {"nome":"Updated","email":"new@email.com","senha":"nova123"}""";

        mockMvc.perform(put("/users/1").with(user("admin@email.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.nome").value("Updated"))
                .andExpect(jsonPath("$.email").value("new@email.com"))
                .andExpect(jsonPath("$.senha").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    void update_asUser_shouldReturn403() throws Exception {
        String body = """
                {"nome":"Updated","email":"new@email.com","senha":"nova123"}""";

        mockMvc.perform(put("/users/1").with(user("maria@email.com").roles("USER"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_withoutAuth_shouldReturn401() throws Exception {
        String body = """
                {"nome":"Updated","email":"new@email.com","senha":"nova123"}""";

        mockMvc.perform(put("/users/1")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void update_userNotFound_returns404() throws Exception {
        when(userService.update(eq(99L), any(User.class)))
                .thenThrow(new UserNotFoundException("User not found"));

        String body = """
                {"nome":"X","email":"x@email.com"}""";

        mockMvc.perform(put("/users/99").with(user("admin@email.com").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_asAdmin_shouldReturn204() throws Exception {
        doNothing().when(userService).delete(1L);

        mockMvc.perform(delete("/users/1").with(user("admin@email.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_asUser_shouldReturn403() throws Exception {
        mockMvc.perform(delete("/users/1").with(user("maria@email.com").roles("USER"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_withoutAuth_shouldReturn401() throws Exception {
        mockMvc.perform(delete("/users/1")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void delete_userNotFound_shouldReturn404() throws Exception {
        doThrow(new UserNotFoundException("User not found")).when(userService).delete(99L);

        mockMvc.perform(delete("/users/99").with(user("admin@email.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }
}