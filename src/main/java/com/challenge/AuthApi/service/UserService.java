package com.challenge.AuthApi.service;

import com.challenge.AuthApi.entity.User;
import com.challenge.AuthApi.exception.UserAlreadyExistsException;
import com.challenge.AuthApi.exception.UserNotFoundException;
import com.challenge.AuthApi.repository.UserRepository;

import com.challenge.AuthApi.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Date;
import java.util.Optional;



@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    //  Criar usuário
    public User createUser(User user) {

        userRepository.findByEmail(user.getEmail()).ifPresent(u -> {
            throw new UserAlreadyExistsException("User already exists with this email");
        });

        // criptografa senha
        String encodedPassword = passwordEncoder.encode(user.getSenha());
        user.setSenha(encodedPassword);

        // define role padrão
        user.setRole("USER");

        return userRepository.save(user);
    }

    // Autenticação (login) - Lança exceções específicas do Spring Security
    public User authenticate(String email, String password) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        if (!passwordEncoder.matches(password, user.getSenha())) {
            throw new BadCredentialsException("Invalid password");
        }

        return user;
    }

    //  Buscar por email - Retorna Optional para maior flexibilidade
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    //  Listar todos
    public Iterable<User> findAll() {
        return userRepository.findAll();
    }

    //  Deletar
    public void delete(Long id) {
        if (!userRepository.existsById(id)) {
            throw new UserNotFoundException("User not found");
        }
        userRepository.deleteById(id);
    }

    public User update(Long id, User updatedUser) {

        User existingUser = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        // Atualiza nome
        if (updatedUser.getNome() != null) {
            existingUser.setNome(updatedUser.getNome());
        }

        // Atualiza email (com validação)
        if (updatedUser.getEmail() != null) {
            userRepository.findByEmail(updatedUser.getEmail())
                    .ifPresent(user -> {
                        if (!user.getId().equals(id)) {
                            throw new UserAlreadyExistsException("Email already in use");
                        }
                    });

            existingUser.setEmail(updatedUser.getEmail());
        }

        if (updatedUser.getSenha() != null && !updatedUser.getSenha().isBlank()) {
            existingUser.setSenha(passwordEncoder.encode(updatedUser.getSenha()));
        }

        if (updatedUser.getRole() != null) {
            existingUser.setRole(updatedUser.getRole());
        }

        return userRepository.save(existingUser);
    }

    public User validateToken(String token, JwtService jwtService) {

        if (!jwtService.isValid(token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }

        String email = jwtService.extractEmail(token);

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não encontrado")
                );
    }
}