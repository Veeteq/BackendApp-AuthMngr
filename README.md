# BackendApp-AuthMngr: Authentication Manager Service

`BackendApp-AuthMngr` is a modern, stateless RESTful backend service built with **Spring Boot 3**, **Spring Security 6**, **Spring Data JPA**, and **Nimbus JOSE (OAuth2 Resource Server)**. It provides robust user authentication, registration, JWT token generation, and account management capabilities based on an OpenAPI 3.0 contract.

---

## System Architecture & Component Dependencies

The complete Authentication Manager solution consists of three decoupled components:

```
+------------------------------------------------------------------+
|                   OpenAPI-AuthManager                            |
|             (API Specification & Schemas)                        |
+------------------------------------------------------------------+
                                |
             +------------------+------------------+
             |                                     |
             v                                     v
+---------------------------+         +----------------------------+
|    BackendApp-AuthMngr    | <=====> |   FrontendApp-AuthManager   |
| (Spring Boot REST Engine) |   HTTP  |   (Angular Standalone SPA) |
+---------------------------+  (JWT)  +----------------------------+
```

### Component Roles & Relationships

* **`OpenAPI-AuthManager`**: Defines the single source of truth for REST endpoints, schemas (`LoginRequest`, `UserRegistration`, `AuthResponse`, `UserResponse`), error structures, and HTTP response codes.
* **`BackendApp-AuthMngr`**: Implements the OpenAPI specification. Handles credential verification, BCrypt password hashing, RS256 JWT issuing/validation, database persistence (H2 in-memory for development), and role-based security filters.
* **`FrontendApp-AuthManager`**: Consumes the backend REST API via Angular Standalone components, functional HTTP interceptors (for `Authorization: Bearer <token>` injection), and Route Guards (`CanActivate`).

---

## Development Milestones & Core Capabilities

The backend application was built in structured, logical development phases:

### Chunk 1 — Project Infrastructure & Dependencies
* **Core Technology Stack**: Spring Boot 3, Java 17+, Maven/Gradle.
* **Key Dependencies**:
    * `spring-boot-starter-web` for REST API endpoints.
    * `spring-boot-starter-security` & `spring-security-oauth2-jose` (Nimbus JOSE) for JWT resource server capabilities.
    * `spring-boot-starter-data-jpa` & `h2` database for persistence.
    * `spring-boot-starter-actuator` for health monitoring.
* **JWT Signing Strategy**: Configured for asymmetric **RS256** (RSA public/private key pairs) for production-grade security.

### Chunk 2 — Domain Model & Persistence Layer
* **Decoupled Domain Architecture**: The `User` JPA entity is kept clean and independent from Spring Security interfaces to avoid serialization leaks and framework coupling.
* **Repositories & Seed Data**: `UserRepository` interface with custom lookup methods (`findByUsername`, `findByEmail`). Seed dev users initialized via database script or runner.
* **Data Transfer Objects**: Implementation of clean request/response models (`LoginRequest`, `UserRegistration`, `AuthResponse`, `UserResponse`).

### Chunk 3 — Security Configuration & JWT Plumbing
* **Stateless Security Filter Chain**: Configured `SessionCreationPolicy.STATELESS` with public access allowed for `/auth/**` endpoints (`/auth/login`, `/auth/register`, `/auth/refresh`) and token protection enforced on `/api/**`.
* **CORS Support**: Integrated `CorsConfigurationSource` to seamlessly accept cross-origin requests from the Angular client (`http://localhost:4200`).
* **Security Adapter**: Created a `UserPrincipal` adapter class implementing `UserDetails` to wrap the `User` entity cleanly inside `CustomUserDetailsService`.
* **Password Encoding**: Integrated `BCryptPasswordEncoder` for hashing user passwords at registration and login verification. Pre-seeded hash values can be generated using online tools like [BCrypt Generator](https://bcrypt-generator.com/).

---

## Quick Start & Running the Application

### Prerequisites
* Java Development Kit (JDK 21 or higher)
* Maven wrapper (`./mvnw`) or Gradle wrapper (`./gradlew`)

### Starting the Server

To start the Spring Boot application locally:

```bash
# Using Maven wrapper
./mvnw spring-boot:run

# Or using Gradle wrapper
./gradlew bootRun
```

The application starts on port `8080` by default.

### Health Check & Monitoring

When Spring Boot Actuator is enabled, verify the server status via:

```
GET http://localhost:8080/actuator/health
```

Expected Response:
```json
{
  "status": "UP"
}
```

### Password Hashing Tool

For manually generating or validating BCrypt password hashes for database seeding or testing, use:
* **BCrypt Generator**: [https://bcrypt-generator.com/](https://bcrypt-generator.com/)

---

## API Summary & Endpoints

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/auth/login` | Public | Authenticates credentials, returns JWT Access Token |
| `POST` | `/auth/register` | Public | Creates a new user account with BCrypt password hashing |
| `POST` | `/auth/refresh` | Public / Cookie | Issues a new Access Token using a valid Refresh Token |
| `GET` | `/api/users/me` | Protected | Retrieves current authenticated user profile |
| `GET` | `/actuator/health` | Public | Returns system operational status (`UP`) |
