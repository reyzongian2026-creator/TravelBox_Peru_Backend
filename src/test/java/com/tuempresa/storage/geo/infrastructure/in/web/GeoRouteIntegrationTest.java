package com.tuempresa.storage.geo.infrastructure.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GeoRouteIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnCurvedFallbackRouteWhenRoutingProviderIsMock() throws Exception {
        perform(mockMvc, get("/api/v1/geo/route")
                        .param("originLat", "-12.122000")
                        .param("originLng", "-77.031000")
                        .param("destinationLat", "-12.110000")
                        .param("destinationLng", "-77.020000")
                        .param("profile", "driving"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("mock-curved"))
                .andExpect(jsonPath("$.fallbackUsed").value(true))
                .andExpect(jsonPath("$.distanceMeters").isNumber())
                .andExpect(jsonPath("$.durationSeconds").isNumber())
                .andExpect(jsonPath("$.points.length()").value(greaterThan(2)));
    }
}

