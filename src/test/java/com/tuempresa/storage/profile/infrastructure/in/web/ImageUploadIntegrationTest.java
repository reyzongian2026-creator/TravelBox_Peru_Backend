package com.tuempresa.storage.profile.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires encryption key configuration and database infrastructure")
class ImageUploadIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldUploadProfilePhoto() throws Exception {
        String email = "photo+" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Photo",
                                  "lastName":"Test",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51991122334",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        MockMultipartFile photo = new MockMultipartFile(
                "file",
                "test-photo.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-png-content".getBytes()
        );

        MvcResult uploadResult = perform(mockMvc, multipart("/api/v1/profile/me/photo")
                        .file(photo)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profilePhotoPath").exists())
                .andReturn();

        String response = uploadResult.getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String profilePhotoPath = json.get("profilePhotoPath").asText();
        
        assertNotNull(profilePhotoPath);
        assertFalse(profilePhotoPath.isBlank());
        assertTrue(profilePhotoPath.contains("profiles") || profilePhotoPath.contains("api/v1/files"));
    }

    @Test
    void shouldRejectUploadWithInvalidContentType() throws Exception {
        String email = "invalid-" + UUID.randomUUID().toString().substring(0, 8) + "@travelbox.pe";
        MvcResult registerResult = perform(mockMvc, post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstName":"Invalid",
                                  "lastName":"Test",
                                  "email":"%s",
                                  "password":"Client123!",
                                  "confirmPassword":"Client123!",
                                  "nationality":"Peru",
                                  "preferredLanguage":"es",
                                  "phone":"+51992233445",
                                  "termsAccepted":true
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String accessToken = objectMapper.readTree(registerResult.getResponse().getContentAsString()).get("accessToken").asText();

        MockMultipartFile textFile = new MockMultipartFile(
                "file",
                "document.txt",
                MediaType.TEXT_PLAIN_VALUE,
                "not an image".getBytes()
        );

        perform(mockMvc, multipart("/api/v1/profile/me/photo")
                        .file(textFile)
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUploadWithoutAuthentication() throws Exception {
        MockMultipartFile photo = new MockMultipartFile(
                "file",
                "unauth-photo.png",
                MediaType.IMAGE_PNG_VALUE,
                "fake-png-content".getBytes()
        );

        perform(mockMvc, multipart("/api/v1/profile/me/photo")
                        .file(photo))
                .andExpect(status().isUnauthorized());
    }
}
