package com.challenge.AuthApi.controller;

import com.challenge.AuthApi.dto.*;
import com.challenge.AuthApi.entity.User;
import com.challenge.AuthApi.service.UserService;
import com.challenge.AuthApi.security.JwtService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/auth")
@Tag(name = "Authentication", description = "Endpoints para autenticação e validação de JWT")
public class AuthController {

    private final UserService userService;
    private final JwtService jwtService;

    public AuthController(UserService userService, JwtService jwtService) {
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @Operation(summary = "Registrar novo usuário", description = "Cria um novo usuário no sistema. O endpoint é público e não requer autenticação.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Usuário criado com sucesso",
                content = @Content(schema = @Schema(implementation = UserResponse.class))),
        @ApiResponse(responseCode = "400", description = "Dados inválidos"),
        @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = new User();
        user.setEmail(request.email());
        user.setSenha(request.senha());
        user.setNome(request.nome());

        User savedUser = userService.createUser(user);

        UserResponse response = new UserResponse(
                savedUser.getId(),
                savedUser.getNome(),
                savedUser.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Autenticar usuário", description = "Autentica um usuário e retorna um JWT.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Autenticação realizada com sucesso",
                content = @Content(schema = @Schema(implementation = AuthResponse.class))),
        @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    })
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            User user = userService.authenticate(
                    request.email(),
                    request.senha()
            );

            String token = jwtService.generateToken(user.getEmail(), user.getRole());

            return ResponseEntity.ok(new AuthResponse(
                    token,
                    user.getEmail(),
                    user.getRole()
            ));

        } catch (AuthenticationException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciais inválidas");
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        }
    }

    @Operation(summary = "Validar token JWT", description = "Valida um token JWT e retorna os dados do usuário associado. Endpoint utilizado pelo CarroAPI para validação de tokens.")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Token válido",
                content = @Content(schema = @Schema(implementation = ValidateTokenResponse.class))),
        @ApiResponse(responseCode = "401", description = "Token inválido ou expirado",
                content = @Content(schema = @Schema(implementation = ValidateTokenResponse.class)))
    })
    @PostMapping("/validate")
    public ResponseEntity<ValidateTokenResponse> validateToken(
            @Valid @RequestBody ValidateTokenRequest request) {

        try {

            User user = userService.validateToken(request.token(), jwtService);

            return ResponseEntity.ok(
                    new ValidateTokenResponse(
                            true,
                            user.getEmail(),
                            user.getRole()
                    )
            );

        } catch (ResponseStatusException e) {

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new ValidateTokenResponse(false, null, null)
            );
        }
    }
}