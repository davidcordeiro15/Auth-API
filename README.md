# 🔐 AuthApi — API de Autenticação e Autorização JWT

>  **Stack:** Java 21 · Spring Boot 4 · Oracle DB · JWT · Bucket4j

---

## Sumário

1. [Visão Geral da API](#1-visão-geral-da-api)
2. [Conceitos de Segurança Utilizados](#2-conceitos-de-segurança-utilizados)
3. [Fluxo de Autenticação](#3-fluxo-de-autenticação)
4. [Estrutura do Token JWT](#4-estrutura-do-token-jwt)
5. [Endpoints da API](#5-endpoints-da-api)
6. [Como usar no Swagger](#6-como-usar-no-swagger)
7. [Como usar no Postman](#7-como-usar-no-postman)
8. [Segurança Implementada](#8-segurança-implementada)
9. [Boas Práticas de Segurança](#9-boas-práticas-de-segurança)
10. [Possíveis Vulnerabilidades e Melhorias](#10-possíveis-vulnerabilidades-e-melhorias)
11. [Como Executar o Projeto](#11-como-executar-o-projeto)
12. [Cenários de Uso](#12-cenários-de-uso)

---

## 1. Visão Geral da API

### Objetivo

A **AuthApi** é uma API RESTful desenvolvida em Java com Spring Boot, cujo propósito central é gerenciar de forma segura o **ciclo de vida de identidade dos usuários**: registro, autenticação (login) e autorização de acesso a recursos protegidos.

A API emite **tokens JWT (JSON Web Token)** após a autenticação bem-sucedida. Esses tokens carregam as informações necessárias para que cada requisição subsequente seja validada sem que o servidor precise consultar o banco de dados a cada chamada.



### Importância da Segurança em APIs

APIs são a "porta dos fundos" de qualquer sistema. Uma API mal protegida expõe:

- **Dados sensíveis** de usuários (emails, senhas, informações pessoais)
- **Operações críticas** (deletar usuários, alterar registros)
- **Acesso não autorizado** a funcionalidades restritas a administradores

A OWASP (Open Web Application Security Project) lista quebra de autenticação e controle de acesso entre as principais vulnerabilidades de APIs. Esta API foi construída justamente para mitigar essas ameaças.

---

## 2. Conceitos de Segurança Utilizados

### 🔑 O que é Autenticação?

**Autenticação** é o processo de verificar **quem você é**. É como mostrar um documento de identidade na portaria de um prédio.

Na AuthApi, a autenticação acontece no endpoint `/auth/login`, onde o usuário prova sua identidade fornecendo e-mail e senha. O sistema verifica se a senha fornecida corresponde ao hash BCrypt armazenado no banco.

```
Usuário diz: "Sou a Maria, minha senha é xpto123"
Sistema verifica: BCrypt.matches("xpto123", hash_do_banco) → ✅ true
Sistema responde: "Identidade confirmada. Aqui está seu token."
```

### 🛡️ O que é Autorização?

**Autorização** é o processo de verificar **o que você pode fazer**. Após saber quem você é, o sistema decide quais recursos você pode acessar.

Exemplo prático:
- **Maria (role: USER)** → pode ver seus dados, mas não pode deletar outros usuários.
- **Carlos (role: ADMIN)** → pode acessar todos os recursos, incluindo listagem e exclusão de usuários.

```
Autenticação = "Você é quem diz ser?"  →  Verifica IDENTIDADE
Autorização  = "Você pode fazer isso?" →  Verifica PERMISSÃO
```

Na AuthApi, a autorização é controlada pela anotação `@PreAuthorize` do Spring Security e pelo papel (role) embutido no token JWT.

### 🎟️ O que é JWT (JSON Web Token)?

JWT é um padrão aberto (RFC 7519) para transmitir informações de forma segura entre partes como um objeto JSON compacto e assinado digitalmente.

A AuthApi usa JWT assinado com o algoritmo **HS256** (HMAC-SHA256), garantindo que qualquer alteração no token seja imediatamente detectada.

### 👥 O que é RBAC (Role-Based Access Control)?

**RBAC** é um modelo de controle de acesso onde as permissões são atribuídas a **papéis (roles)**, e os usuários recebem papéis. Em vez de dar permissões individuais a cada usuário, você define:

```
┌──────────────┐       ┌──────────────────────────────────┐
│  Role: USER  │ ───▶  │ • Ver próprio perfil             │
│              │       │ • Acessar recursos básicos       │
└──────────────┘       └──────────────────────────────────┘

┌──────────────┐       ┌──────────────────────────────────┐
│  Role: ADMIN │ ───▶  │ • Tudo do USER +                 │
│              │       │ • Listar todos os usuários       │
│              │       │ • Deletar usuários               │
│              │       │ • Atualizar qualquer registro    │
└──────────────┘       └──────────────────────────────────┘
```

Na AuthApi, a role é definida no momento do registro e armazenada tanto no banco quanto dentro do payload do JWT. O Spring Security lê a role do token e aplica as restrições automaticamente via `@EnableMethodSecurity`.

---

## 3. Fluxo de Autenticação

### Visão Macro

```
┌──────────┐         ┌───────────────┐         ┌──────────────┐
│  Cliente │         │    AuthApi    │         │   Oracle DB  │
└────┬─────┘         └──────┬────────┘         └──────┬───────┘
     │                      │                         │
     │  POST /auth/login    │                         │
     │  {email, senha}      │                         │
     │─────────────────────▶│                         │
     │                      │  SELECT * FROM USUARIOS │
     │                      │  WHERE email = ?        │
     │                      │────────────────────────▶│
     │                      │                         │
     │                      │◀────────────────────────│
     │                      │  Retorna usuário        │
     │                      │                         │
     │                      │ BCrypt.matches(senha)   │
     │                      │ JwtService.generate()   │
     │                      │                         │
     │◀─────────────────────│                         │
     │  {token, email, role}│                         │
     │                      │                         │
     │  GET /users          │                         │
     │  Authorization:      │                         │
     │  Bearer eyJhbGci... │                         │
     │─────────────────────▶│                         │
     │                      │ JwtFilter valida token  │
     │                      │ Extrai email + role     │
     │                      │ Autentica no contexto   │
     │                      │                         │
     │◀─────────────────────│                         │
     │  200 OK + dados      │                         │
```

### Passo a Passo Detalhado

#### Passo 1 — Registro do Usuário (`POST /auth/register`)

O usuário envia seus dados. O sistema:
1. Valida os campos via Bean Validation (`@NotBlank`, `@Email`, `@Size`)
2. Verifica se o e-mail já existe no banco (evita duplicatas)
3. **Criptografa a senha** com BCrypt antes de salvar
4. Define a role (padrão: `USER` se não informada)
5. Persiste o usuário no Oracle DB

```java
// UserService.java — Senha NUNCA é salva em texto puro
String encodedPassword = passwordEncoder.encode(user.getSenha());
user.setSenha(encodedPassword);
```

#### Passo 2 — Login e Geração do Token (`POST /auth/login`)

O usuário envia e-mail e senha. O sistema:
1. Busca o usuário pelo e-mail
2. Compara a senha fornecida com o hash BCrypt: `passwordEncoder.matches(senha, hash)`
3. Se válido, chama `JwtService.generateToken(email, role)`
4. Retorna o token JWT no corpo da resposta

```java
// JwtService.java — Geração do token
public String generateToken(String email, String role) {
    return Jwts.builder()
            .setSubject(email)           // "sub": identidade
            .claim("role", role)         // claim customizado
            .setIssuedAt(new Date())     // "iat": momento de emissão
            .setExpiration(new Date(System.currentTimeMillis() + expiration)) // "exp"
            .signWith(getKey(), SignatureAlgorithm.HS256) // assinatura HMAC-SHA256
            .compact();
}
```

#### Passo 3 — Uso do Token nas Requisições

A partir deste momento, cada requisição a endpoints protegidos deve incluir o token no cabeçalho HTTP:

```http
GET /users HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJ...
```

O prefixo `Bearer` é obrigatório. É um padrão definido pela RFC 6750.

#### Passo 4 — Validação do Token pelo JwtFilter

Para cada requisição em endpoint protegido, o `JwtFilter` intercepta antes de chegar ao controller:

1. Lê o cabeçalho `Authorization`
2. Extrai o token (remove o prefixo `Bearer `)
3. Chama `jwtService.isValid(token)` — verifica assinatura e expiração
4. Extrai `email` e `role` do payload
5. Cria um objeto de autenticação e registra no `SecurityContextHolder`
6. Se qualquer passo falhar, o contexto é limpo e a requisição retorna 401

```java
// JwtFilter.java — Núcleo da validação
if (jwtService.isValid(token)) {
    String email = jwtService.extractEmail(token);
    String role  = jwtService.extractRole(token);

    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken(
            email, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

---

## 4. Estrutura do Token JWT

Um JWT é composto por três partes separadas por ponto (`.`):

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJpYUBlbWFpbC5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTcxMjAwMDAwMCwiZXhwIjoxNzEyMDAzNjAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

### 4.1 Header (Cabeçalho)

```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

| Campo | Valor    | Significado                                    |
|-------|----------|------------------------------------------------|
| `alg` | `HS256`  | Algoritmo de assinatura (HMAC com SHA-256)     |
| `typ` | `JWT`    | Tipo do token                                  |

O **HS256** é um algoritmo simétrico: a mesma chave secreta é usada para assinar e validar. A chave está em `application.properties` no campo `jwt.secret`.

### 4.2 Payload (Corpo)

```json
{
  "sub":  "maria@email.com",
  "role": "USER",
  "iat":  1712000000,
  "exp":  1712003600
}
```

| Campo  | Tipo    | Significado                                                      |
|--------|---------|------------------------------------------------------------------|
| `sub`  | String  | **Subject** — identifica o dono do token (e-mail do usuário)    |
| `role` | String  | **Claim customizado** — papel do usuário (USER ou ADMIN)        |
| `iat`  | Unix TS | **Issued At** — timestamp de emissão do token                   |
| `exp`  | Unix TS | **Expiration** — timestamp de expiração (iat + 3.600.000 ms)    |

> ⚠️ **O payload é apenas codificado em Base64, não criptografado.** Qualquer pessoa pode decodificar e ler seu conteúdo. Por isso, **nunca coloque dados sensíveis** (como senha, CPF, cartão de crédito) no payload do JWT.

### 4.3 Signature (Assinatura)

```
HMACSHA256(
  base64url(header) + "." + base64url(payload),
  SECRET_KEY
)
```

A assinatura é calculada sobre o header + payload usando a chave secreta. Se alguém alterar qualquer caractere do payload, a assinatura não baterá e o token será rejeitado pelo `JwtService.isValid()`.

```java
// JwtService.java — Verificação da assinatura
private Claims getClaims(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(getKey())   // Usa a mesma chave secreta
            .build()
            .parseClaimsJws(token)     // Lança JwtException se inválido
            .getBody();
}
```

### Visualização completa

```
                  HEADER                    PAYLOAD                     SIGNATURE
┌────────────────────────────┐  ┌──────────────────────────────┐  ┌────────────────────┐
│ eyJhbGciOiJIUzI1NiJ9       │.│ eyJzdWIiOiJtYXJpYUBl...      │.│ SflKxwRJSMeKKF2Q...│
│ {"alg":"HS256","typ":"JWT"} │  │ {"sub":"...","role":"USER"…} │  │ HMAC-SHA256(H+P,K) │
└────────────────────────────┘  └──────────────────────────────┘  └────────────────────┘
      Base64Url encoded               Base64Url encoded               Não decodificável
                                     (legível, não seguro!)           sem a chave secreta
```

---

## 5. Endpoints da API

### Base URL: `http://localhost:8080`

---

### `POST /auth/register` — Registrar Usuário

**Função:** Cria um novo usuário no sistema com senha criptografada.

**Acesso:** Público (não requer autenticação)

**Request:**
```http
POST /auth/register
Content-Type: application/json

{
  "nome":  "Maria Silva",
  "email": "maria@email.com",
  "senha": "senha123",
  "role":  "USER"
}
```

| Campo   | Tipo   | Validação                            | Obrigatório |
|---------|--------|--------------------------------------|-------------|
| `nome`  | String | `@Size(min=3, max=100)` `@NotBlank`  | ✅ Sim      |
| `email` | String | `@Email` `@NotBlank`                 | ✅ Sim      |
| `senha` | String | `@Size(min=6)` `@NotBlank`           | ✅ Sim      |
| `role`  | String | `@Size(min=3, max=100)` `@NotBlank`  | ✅ Sim      |

**Response — 201 Created:**
```json
{
  "id":    1,
  "nome":  "Maria Silva",
  "email": "maria@email.com"
}
```
> A senha e a role **não retornam** na resposta. Princípio do menor privilégio de exposição de dados.

**Respostas de Erro:**

| Status | Motivo                              |
|--------|-------------------------------------|
| `400`  | Campos inválidos (validação falhou) |
| `409`  | E-mail já cadastrado                |

**Segurança aplicada:**
- Bean Validation antes de qualquer processamento
- Senha hasheada com BCrypt (fator de custo padrão = 10)
- E-mail verificado de unicidade via query parametrizada (prevenção de SQL Injection)
- `UserAlreadyExistsException` sem expor detalhes internos

---

### `POST /auth/login` — Autenticar Usuário

**Função:** Autentica o usuário e retorna um token JWT válido.

**Acesso:** Público (não requer autenticação)

**Request:**
```http
POST /auth/login
Content-Type: application/json

{
  "email": "maria@email.com",
  "senha": "senha123"
}
```

**Response — 200 OK:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJtYXJpYUBlbWFpbC5jb20iLCJyb2xlIjoiVVNFUiIsImlhdCI6MTcxMjAwMDAwMCwiZXhwIjoxNzEyMDAzNjAwfQ.SflKxwRJSMeKKF2Q",
  "email": "maria@email.com",
  "role":  "USER"
}
```

**Respostas de Erro:**

| Status | Motivo                         |
|--------|--------------------------------|
| `400`  | Campos inválidos ou ausentes   |
| `401`  | Credenciais inválidas          |

**Segurança aplicada:**
- `BCryptPasswordEncoder.matches()` — comparação segura de senhas
- Mensagem de erro genérica `"Credenciais inválidas"` (não diferencia e-mail incorreto de senha incorreta, evitando enumeração de usuários)
- Token com expiração em 1 hora (`jwt.expiration=3600000` ms)

---

### `POST /auth/validate` — Validar Token

**Função:** Verifica se um token JWT é válido e retorna os dados do usuário associado.

**Acesso:** Público (usado por outros serviços da arquitetura)

**Request:**
```http
POST /auth/validate
Content-Type: application/json

{
  "token": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Response — 200 OK (token válido):**
```json
{
  "valid": true,
  "email": "maria@email.com",
  "role":  "USER"
}
```

**Response — 401 Unauthorized (token inválido):**
```json
{
  "valid": false,
  "email": null,
  "role":  null
}
```

**Segurança aplicada:**
- Verificação de assinatura HMAC-SHA256
- Verificação de expiração (`exp`)
- Verificação de existência do usuário no banco

---

### `GET /users` — Listar Usuários

**Função:** Retorna todos os usuários cadastrados.

**Acesso:** 🔒 Requer token JWT válido no cabeçalho

**Request:**
```http
GET /users
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 200 OK:**
```json
[
  {
    "id":    1,
    "nome":  "Maria Silva",
    "email": "maria@email.com",
    "role":  "USER"
  },
  {
    "id":    2,
    "nome":  "Carlos Admin",
    "email": "carlos@email.com",
    "role":  "ADMIN"
  }
]
```

**Respostas de Erro:**

| Status | Motivo                            |
|--------|-----------------------------------|
| `401`  | Token ausente, inválido ou expirado |
| `429`  | Rate limit excedido (> 20 req/min) |

**Segurança aplicada:**
- `JwtFilter` intercepta e valida o token antes do controller
- Rate limiting por IP (Bucket4j — 20 requisições por minuto)

---

### `PUT /users/{id}` — Atualizar Usuário

**Função:** Atualiza os dados de um usuário existente.

**Acesso:** 🔒 Requer token JWT válido

**Request:**
```http
PUT /users/1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
Content-Type: application/json

{
  "nome":  "Maria Souza",
  "email": "maria.souza@email.com",
  "senha": "novaSenha456"
}
```

**Response — 200 OK:** Retorna o objeto `User` atualizado.

**Segurança aplicada:**
- Token JWT validado pelo `JwtFilter`
- Nova senha criptografada com BCrypt
- Verificação de unicidade do novo e-mail

---

### `DELETE /users/{id}` — Deletar Usuário

**Função:** Remove um usuário do sistema pelo ID.

**Acesso:** 🔒 Requer token JWT válido

**Request:**
```http
DELETE /users/1
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**Response — 204 No Content** (sem corpo na resposta)

**Segurança aplicada:**
- Token JWT validado
- Verificação de existência do usuário antes de deletar (evita deleção de ID inexistente com erro genérico)

---

## 6. Como usar no Swagger

O projeto inclui o **Springdoc OpenAPI** que gera automaticamente a documentação interativa da API.

### Acessar o Swagger UI

Com o projeto rodando, acesse:
```
http://localhost:8080/swagger-ui/index.html
```

### Passo 1 — Fazer Login

1. Localize o endpoint **`POST /auth/login`**
2. Clique em **"Try it out"**
3. Preencha o body com suas credenciais:
```json
{
  "email": "maria@email.com",
  "senha": "senha123"
}
```
4. Clique em **"Execute"**
5. No campo **Response Body**, você verá o token JWT:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "maria@email.com",
  "role":  "USER"
}
```

### Passo 2 — Copiar o Token

Copie **apenas o valor** do campo `token` (sem aspas).

### Passo 3 — Usar o Botão "Authorize"

1. No canto superior direito do Swagger UI, clique no botão **🔒 Authorize**
2. Na janela que abrir, no campo **"bearerAuth (http, Bearer)"**, cole o token copiado
3. Clique em **"Authorize"** e depois em **"Close"**

O Swagger passará automaticamente o cabeçalho `Authorization: Bearer <seu_token>` em todas as requisições.

### Passo 4 — Testar Endpoints Protegidos

1. Localize o endpoint **`GET /users`**
2. Clique em **"Try it out"** → **"Execute"**
3. Observe o cabeçalho de requisição gerado — ele incluirá automaticamente o Bearer Token
4. Você deverá receber `200 OK` com a lista de usuários

> Se receber `401 Unauthorized`, o token pode ter expirado (válido por 1 hora). Repita o processo de login.

---

## 7. Como usar no Postman

### Passo 1 — Criar Requisição de Login

1. Clique em **New** → **HTTP Request**
2. Configure:
   - **Método:** `POST`
   - **URL:** `http://localhost:8080/auth/login`
3. Vá na aba **Body** → selecione **raw** → **JSON**
4. Cole o body:
```json
{
  "email": "maria@email.com",
  "senha": "senha123"
}
```
5. Clique em **Send**

### Passo 2 — Capturar o Token

Na resposta, copie o valor de `token`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdW..."
}
```

**Dica avançada:** Use o **Postman Scripts (Tests)** para capturar o token automaticamente:

```javascript
// Aba "Scripts" → "Post-response" no endpoint de login
const response = pm.response.json();
pm.environment.set("jwt_token", response.token);
```

Isso salva o token em uma variável de ambiente chamada `jwt_token`.

### Passo 3 — Usar Bearer Token

Em uma nova requisição para endpoint protegido:

1. Vá na aba **Authorization**
2. Em **Auth Type**, selecione **Bearer Token**
3. No campo **Token**, cole o token (ou use `{{jwt_token}}` se salvou na variável)

Alternativa manual via Headers:
```
Key:   Authorization
Value: Bearer eyJhbGciOiJIUzI1NiJ9...
```

### Passo 4 — Testar Endpoints Protegidos

**Listar Usuários:**
```
Método: GET
URL: http://localhost:8080/users
Headers: Authorization: Bearer {{jwt_token}}
```

**Deletar Usuário:**
```
Método: DELETE
URL: http://localhost:8080/users/1
Headers: Authorization: Bearer {{jwt_token}}
```

**Atualizar Usuário:**
```
Método: PUT
URL: http://localhost:8080/users/1
Headers: Authorization: Bearer {{jwt_token}}
Body (JSON):
{
  "nome": "Novo Nome",
  "senha": "novaSenha456"
}
```

---

## 8. Segurança Implementada

### ✅ Validação de Entrada

A API usa **Bean Validation** (Jakarta Validation) em todos os DTOs de entrada. As anotações aplicadas são:

```java
// RegisterRequest.java
@NotBlank @Size(min = 3, max = 100) String nome,
@Email @NotBlank                    String email,
@NotBlank @Size(min = 6)            String senha,
@NotBlank @Size(min = 3, max = 100) String role
```

O `GlobalExceptionHandler` captura `MethodArgumentNotValidException` e retorna uma resposta estruturada com os campos inválidos — sem expor stack trace:

```json
{
  "email": "must be a well-formed email address",
  "senha": "size must be between 6 and 2147483647"
}
```

### ✅ Proteção contra SQL Injection

A AuthApi usa **Spring Data JPA com consultas parametrizadas** geradas automaticamente pelo Hibernate. O repositório utiliza `JpaRepository` e métodos derivados como `findByEmail(String email)`, onde o Spring cria queries preparadas (`PreparedStatement`), tornando SQL Injection impossível nessas operações.

```java
// UserRepository.java — query parametrizada gerada automaticamente
Optional<User> findByEmail(String email);
// Gera internamente: SELECT * FROM USUARIOS WHERE email = ?
//                                                         ↑ parâmetro seguro
```

### ✅ Proteção contra XSS (Cross-Site Scripting)

Por ser uma API REST que retorna `application/json` (e não HTML), o vetor de ataque XSS clássico (injeção de scripts em páginas HTML) não se aplica diretamente. Adicionalmente:

- O Spring Security desabilita a renderização de conteúdo inline por padrão
- Os dados passam por validação antes de serem persistidos
- As respostas utilizam DTOs que mapeiam apenas os campos necessários, sem refletir input bruto

Em ambientes com frontend, a proteção XSS deve ser implementada na camada de apresentação, mas a API não cria brechas ao não renderizar HTML.

### ✅ Uso de HTTPS (Conceitual)

Em produção, toda comunicação com a API **deve** ocorrer via HTTPS (TLS/SSL). O HTTPS garante:

- **Confidencialidade:** O token JWT trafega criptografado; sem HTTPS, qualquer pessoa na rede pode interceptá-lo (ataque Man-in-the-Middle)
- **Integridade:** Garante que o token não foi alterado em trânsito
- **Autenticidade:** Confirma que você está falando com o servidor legítimo

Para habilitar HTTPS no Spring Boot, configure em `application.properties`:
```properties
server.ssl.key-store=classpath:keystore.p12
server.ssl.key-store-type=PKCS12
server.ssl.key-store-password=sua_senha
server.ssl.key-alias=sua_alias
server.port=8443
```

### ✅ Tokens com Expiração

O token JWT tem validade configurada em `application.properties`:

```properties
jwt.expiration=3600000  # 1 hora em milissegundos
```

Isso limita a janela de ataque: mesmo que um token seja comprometido, ele deixa de funcionar após 1 hora. O `JwtService.isValid()` verifica a expiração automaticamente:

```java
// JwtService.java — JwtException é lançada se expirado
private Claims getClaims(String token) {
    return Jwts.parserBuilder()
            .setSigningKey(getKey())
            .build()
            .parseClaimsJws(token)  // valida "exp" automaticamente
            .getBody();
}
```

### ✅ Controle de Acesso por Roles

A configuração `@EnableMethodSecurity` no `SecurityConfig` habilita o controle de acesso por anotações:

```java
@SecurityConfig
@EnableMethodSecurity  // ← Habilita @PreAuthorize, @PostAuthorize, etc.
public class SecurityConfig { ... }
```

O `JwtFilter` extrai a role do token e registra no contexto de segurança com o prefixo `ROLE_`:

```java
new SimpleGrantedAuthority("ROLE_" + role)
// Para role="ADMIN" → "ROLE_ADMIN"
// Para role="USER"  → "ROLE_USER"
```

Nos controllers, o acesso pode ser restrito por role:

```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<List<User>> getAllUsers() { ... }
```

---

## 9. Boas Práticas de Segurança

### 🚫 Não Expor StackTrace

O `GlobalExceptionHandler` com `@RestControllerAdvice` intercepta **todas as exceções** antes que cheguem ao cliente. O handler catch-all garante que erros inesperados nunca exponham detalhes internos:

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<String> handleGenericException(Exception ex) {
    // Stack trace é logado internamente (não exposto ao cliente)
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body("Ocorreu um erro interno no servidor.");
    // ↑ Mensagem genérica, sem detalhes técnicos
}
```

Um atacante que recebe stack traces pode descobrir: versão do framework, estrutura de pastas, nomes de classes e queries SQL.

### 🧹 Sanitização de Dados

- **Senhas** nunca são armazenadas em texto puro — sempre hasheadas com BCrypt
- **Respostas** usam DTOs especializados (`UserResponse`, `AuthResponse`) que omitem campos sensíveis como `senha`
- **Inputs** são validados antes de qualquer processamento com Bean Validation

### 🔒 Proteção de Endpoints

A cadeia de segurança é configurada em camadas no `SecurityConfig`:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
    .anyRequest().authenticated()  // ← Tudo mais requer autenticação
)
.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
```

Sessões são completamente desabilitadas (`STATELESS`), eliminando ataques baseados em session fixation:

```java
.sessionManagement(session ->
    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
)
```

---

## 10. Possíveis Vulnerabilidades e Melhorias

### 🔄 Refresh Token

**Problema atual:** Quando o token expira (1 hora), o usuário precisa fazer login novamente. Isso cria uma tradeoff: tokens com expiração longa são inseguros; tokens curtos afetam a UX.

**Solução:** Implementar um par de tokens:
- `access_token`: curta duração (15 minutos) — usado nas requisições
- `refresh_token`: longa duração (7 dias) — usado apenas para obter novo `access_token`

```
POST /auth/refresh
Body: { "refresh_token": "..." }
Response: { "access_token": "novo_token..." }
```

### 🚦 Rate Limiting

**Status atual:** ✅ **Implementado via Bucket4j** para endpoints protegidos.

```java
// RateLimitFilter.java — 20 requisições por minuto por IP
Bandwidth.classic(20, Refill.greedy(20, Duration.ofMinutes(1)))
```

**Melhoria sugerida:** O rate limiting atual não cobre os endpoints `/auth/**`. Adicionar rate limiting no login previne ataques de força bruta:

```java
// Sugestão: limite mais restrito no endpoint de login
Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(1))) // 5 tentativas/min
```

### 🌐 CORS (Cross-Origin Resource Sharing)

**Problema:** Sem configuração de CORS, aplicações frontend em domínios diferentes podem ou não conseguir acessar a API (comportamento depende da configuração padrão do Spring).

**Solução:** Configurar CORS explicitamente:

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://meuapp.com"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    // ...
}
```

### 📋 Auditoria e Logs

**Situação atual:** Logs de SQL e segurança estão habilitados em `application.properties`:
```properties
logging.level.org.springframework.security=DEBUG
logging.level.org.hibernate.SQL=DEBUG
```

**Melhoria:** Implementar auditoria de eventos de segurança:
- Tentativas de login (bem-sucedidas e falhas)
- Acessos a endpoints protegidos
- Operações de criação, atualização e exclusão de usuários

```java
log.warn("Tentativa de login falha para: {}", email);
log.info("Usuário {} acessou GET /users", email);
```

### 🔐 Criptografia de Dados Sensíveis

**Problema identificado:** O `application.properties` contém:
- Credenciais do banco de dados em texto puro (`spring.datasource.password=150705`)
- Chave secreta JWT em texto puro (`jwt.secret=MinhaChave...`)

**Solução para produção:**

1. **Variáveis de ambiente:**
```properties
spring.datasource.password=${DB_PASSWORD}
jwt.secret=${JWT_SECRET}
```

2. **Spring Cloud Config / HashiCorp Vault** para gerenciamento centralizado de segredos

3. **Rotação periódica** da chave JWT — invalida todos os tokens existentes forçando re-autenticação

---

## 11. Como Executar o Projeto

### Pré-requisitos

| Tecnologia       | Versão Mínima | Verificar                  |
|-----------------|---------------|----------------------------|
| Java (JDK)      | 21            | `java -version`            |
| Maven           | 3.9+          | `mvn -version`             |
| Oracle Database | 19c+          | Conexão com FIAP           |

### Passo 1 — Clonar o repositório

```bash
git clone [https://github.com/seu-usuario/CarroAuth-Challenge.git](https://github.com/davidcordeiro15/Auth-API.git)
cd CarroAuth-Challenge
```

### Passo 2 — Configurar o Banco de Dados

Edite `src/main/resources/application.properties` com suas credenciais Oracle:

```properties
spring.datasource.url=jdbc:oracle:thin:@oracle.fiap.com.br:1521:orcl
spring.datasource.username=SEU_RM
spring.datasource.password=SUA_SENHA
```

> O Hibernate criará/atualizará a tabela `USUARIOS` automaticamente (`ddl-auto=update`).

### Passo 3 — Compilar e Executar

```bash
# Compilar e executar com Maven Wrapper
./mvnw spring-boot:run

# Ou no Windows:
mvnw.cmd spring-boot:run

# Ou compilar o JAR e executar:
./mvnw clean package -DskipTests
java -jar target/AuthApi-0.0.1-SNAPSHOT.jar
```

### Passo 4 — Verificar se está rodando

```bash
curl http://localhost:8080/swagger-ui/index.html
# Deve retornar o HTML da documentação
```

### Passo 5 — Testar Autenticação

**Registrar um usuário admin:**
```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"nome":"Admin","email":"admin@api.com","senha":"admin123","role":"ADMIN"}'
```

**Fazer login e obter token:**
```bash
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@api.com","senha":"admin123"}'
```

**Usar o token para acessar endpoint protegido:**
```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."  # Cole o token aqui

curl http://localhost:8080/users \
  -H "Authorization: Bearer $TOKEN"
```

**Testar token expirado ou inválido:**
```bash
curl http://localhost:8080/users \
  -H "Authorization: Bearer token_invalido"
# Esperado: 401 Unauthorized
```

---

## 12. Cenários de Uso

### Cenário 1 — Usuário Comum Acessando a API

**Contexto:** Maria é uma usuária com role `USER` que quer atualizar seus dados.

```
1. Maria faz POST /auth/login com email + senha
   └─▶ API retorna JWT com role=USER e exp=1hora

2. Maria usa o token para PUT /users/1 (seu ID)
   └─▶ JwtFilter valida token
   └─▶ ROLE_USER é registrado no SecurityContext
   └─▶ Controller processa a atualização ✅

3. Maria tenta acessar DELETE /users/2 (outro usuário)
   └─▶ JwtFilter valida token (ainda é válido)
   └─▶ ROLE_USER não tem permissão via @PreAuthorize
   └─▶ API retorna 403 Forbidden ❌
   └─▶ Princípio do menor privilégio aplicado ✅

4. Após 1 hora, Maria tenta fazer GET /users
   └─▶ JwtFilter detecta token expirado (exp < now)
   └─▶ API retorna 401 Unauthorized
   └─▶ Maria deve fazer login novamente ✅
```

### Cenário 2 — Admin Acessando Recursos Restritos

**Contexto:** Carlos é administrador com role `ADMIN` gerenciando usuários do sistema.

```
1. Carlos faz POST /auth/login
   └─▶ API retorna JWT com role=ADMIN

2. Carlos faz GET /users
   └─▶ JwtFilter extrai role=ADMIN do token
   └─▶ ROLE_ADMIN tem acesso liberado
   └─▶ Lista completa de usuários retornada ✅

3. Carlos identifica conta suspeita e faz DELETE /users/5
   └─▶ Token validado + ROLE_ADMIN autorizado
   └─▶ Usuário removido do banco ✅

4. Carlos tenta usar token de outro serviço (JWT de sistema externo)
   └─▶ JwtFilter tenta verificar assinatura com a SECRET_KEY local
   └─▶ Assinatura não bate (chave diferente)
   └─▶ API retorna 401 Unauthorized
   └─▶ Integridade da assinatura protege o sistema ✅
```

### Cenário 3 — Atacante Tentando Invadir a API

**Contexto:** Um atacante tenta burlar a segurança da API.

```
❌ Tentativa 1: SQL Injection no login
   POST /auth/login {"email":"' OR '1'='1"}
   └─▶ @Email valida formato → 400 Bad Request
   └─▶ JPA usa PreparedStatement → SQL Injection impossível

❌ Tentativa 2: Força bruta de senhas
   Múltiplos POST /auth/login em sequência
   └─▶ Sem rate limiting no /auth (ponto de melhoria!)
   └─▶ BCrypt com custo 10 torna cada tentativa lenta (~100ms)
   └─▶ 1000 tentativas = ~100 segundos apenas para computar

❌ Tentativa 3: Falsificar um token JWT como ADMIN
   Decodificar payload, alterar role para ADMIN, recodificar
   └─▶ Assinatura HMAC-SHA256 não bate (chave secreta desconhecida)
   └─▶ JwtService.isValid() retorna false → 401 Unauthorized

❌ Tentativa 4: Reutilizar token após logout
   └─▶ Não há blacklist de tokens (ponto de melhoria!)
   └─▶ Token permanece válido até expirar
   └─▶ Mitigação: expiração curta (1 hora) limita a janela de risco
```

---

## Stack Tecnológica

| Tecnologia                   | Versão    | Uso                                          |
|-----------------------------|-----------|----------------------------------------------|
| Java                        | 21        | Linguagem principal                          |
| Spring Boot                 | 4.0.6     | Framework web e IoC                          |
| Spring Security             | (via Boot) | Filtros, autenticação e autorização          |
| JJWT (jjwt-api/impl)       | 0.11.5    | Geração e validação de tokens JWT            |
| BCryptPasswordEncoder        | (via Boot) | Hash de senhas                               |
| Spring Data JPA + Hibernate | (via Boot) | Acesso ao banco de dados com ORM             |
| Oracle Database             | 19c+      | Banco de dados relacional                    |
| Bucket4j                    | 8.10.1    | Rate limiting por token de bucket            |
| Springdoc OpenAPI           | 3.0.2     | Documentação automática (Swagger UI)         |
| Lombok                      | (via Boot) | Redução de boilerplate (getters/setters)     |
| Bean Validation (Jakarta)   | (via Boot) | Validação declarativa de inputs              |

---

## Estrutura do Projeto

```
src/
├── main/java/com/challenge/AuthApi/
│   ├── AuthApiApplication.java          # Entry point Spring Boot
│   ├── config/
│   │   ├── SecurityConfig.java          # Filtros, sessão, CSRF, autorização
│   │   └── SwaggerConfig.java           # Configuração do Bearer no OpenAPI
│   ├── controller/
│   │   ├── AuthController.java          # /auth/register, /login, /validate
│   │   └── UserController.java          # /users (CRUD protegido)
│   ├── dto/                             # Data Transfer Objects (sem expor entidades)
│   │   ├── AuthResponse.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   ├── UserResponse.java
│   │   ├── ValidateTokenRequest.java
│   │   └── ValidateTokenResponse.java
│   ├── entity/
│   │   └── User.java                    # Entidade JPA mapeada para USUARIOS
│   ├── exception/
│   │   ├── GlobalExceptionHandler.java  # Tratamento centralizado de erros
│   │   ├── UserAlreadyExistsException.java
│   │   └── UserNotFoundException.java
│   ├── repository/
│   │   └── UserRepository.java          # JpaRepository com findByEmail
│   ├── security/
│   │   ├── JwtFilter.java               # Intercepta e valida tokens JWT
│   │   ├── JwtService.java              # Geração, validação e extração de claims
│   │   └── RateLimitFilter.java         # Rate limiting com Bucket4j
│   └── service/
│       └── UserService.java             # Regras de negócio + autenticação
└── resources/
    └── application.properties           # Configurações da aplicação
```

---
