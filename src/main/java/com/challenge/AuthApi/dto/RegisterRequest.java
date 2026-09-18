package com.challenge.AuthApi.dto;

import jakarta.validation.constraints.*;



public record RegisterRequest(

        @NotBlank
        @Size(min = 3, max = 100)
        String nome,

        @Email
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 6)
        String senha
) {}