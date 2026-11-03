package com.veeteq.auth.authservice.rest.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class SystemControllerTest {

    @Autowired
    private RestTemplateBuilder restTemplateBuilder;

    @Autowired
    private MockMvc mockMvc;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("GET /actuator/health - Powinien zwrócić status UP")
    public void testHealthEndpoint() throws Exception {
        ResponseEntity<JsonNode> response = restTemplateBuilder
                .rootUri("http://localhost:" + managementPort + "/actuator")
                .build()
                .exchange("/health", HttpMethod.GET, new HttpEntity<>(null), JsonNode.class);

        assertTrue(response.getStatusCode().is2xxSuccessful());
        var body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.at("/status").asText());
    }

    @Test
    @DisplayName("GET /api/hello - 401 Unauthorized dla użytkownika niezalogowanego")
    public void testHelloEndpointUnauthorized() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/hello - 200 OK dla użytkownika zalogowanego")
    @WithMockUser
    public void testHelloEndpointAuthorized() throws Exception {
        mockMvc.perform(get("/api/hello"))
                .andExpect(status().isOk());
    }
}