package com.tuempresa.storage.ops.infrastructure.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuempresa.storage.ops.application.usecase.OpsQrHandoffService;
import com.tuempresa.storage.shared.infrastructure.security.AuthUserPrincipal;
import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Disabled("Requires database infrastructure")
class OpsQrHandoffWorkflowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OpsQrHandoffService opsQrHandoffService;

    @Test
    void shouldBlockDeliveryValidationStepsWhenOrderIsNotRespected() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), false, true);
        moveReservationToOutForDelivery(clientToken, operatorToken, reservationId);

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/request-approval", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageForOperator":"Validar entrega",
                                  "messageForCustomerSpanish":"Validaremos tu entrega",
                                  "customerLanguage":"es"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_STEP_ORDER_INVALID"));

        perform(mockMvc, patch("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/luggage", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": true
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_STEP_ORDER_INVALID"));

        perform(mockMvc, patch("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/identity", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityValidated").value(true));

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/request-approval", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageForOperator":"Validar entrega",
                                  "messageForCustomerSpanish":"Validaremos tu entrega",
                                  "customerLanguage":"es"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_STEP_ORDER_INVALID"));

        perform(mockMvc, patch("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/luggage", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "value": true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.luggageMatched").value(true));

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/request-approval", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageForOperator":"Validar entrega",
                                  "messageForCustomerSpanish":"Validaremos tu entrega",
                                  "customerLanguage":"es"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorApprovalRequested").value(true));

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/delivery/request-approval", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "messageForOperator":"Validar entrega",
                                  "messageForCustomerSpanish":"Validaremos tu entrega",
                                  "customerLanguage":"es"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DELIVERY_APPROVAL_ALREADY_PENDING"));
    }

    @Test
    void shouldBlockPickupPinEndpointForDeliveryFlow() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), false, true);
        moveReservationToOutForDelivery(clientToken, operatorToken, reservationId);

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/pickup/confirm", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pin":"123456",
                                  "notes":"Intento fuera de flujo"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PICKUP_FLOW_REQUIRED"));
    }

    @Test
    void shouldExposePickupPinAndSharedLuggagePhotosToClientAfterPickupFlow() throws Exception {
        String clientToken = loginAndGetAccessToken("client@travelbox.pe", "Client123!");
        String operatorToken = loginAndGetAccessToken("operator@travelbox.pe", "Operator123!");
        Warehouse warehouse = warehouseForScopedOps();

        long reservationId = createAndConfirmReservation(clientToken, warehouse.getId(), true, false);

        MockMultipartFile clientHandoff = new MockMultipartFile(
                "file",
                "client-handoff.png",
                MediaType.IMAGE_PNG_VALUE,
                "client-handoff-evidence".getBytes()
        );

        perform(mockMvc, multipart("/api/v1/inventory/evidences/upload")
                        .file(clientHandoff)
                        .header("Authorization", "Bearer " + clientToken)
                        .param("reservationId", String.valueOf(reservationId))
                        .param("type", "CLIENT_HANDOFF_PHOTO")
                        .param("observation", "Foto inicial del cliente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("EVIDENCE_REGISTERED"));

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/tag", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bagUnits": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bagTagId").isNotEmpty());

        MockMultipartFile warehousePhoto = new MockMultipartFile(
                "files",
                "warehouse-bag-1.png",
                MediaType.IMAGE_PNG_VALUE,
                "warehouse-bag-evidence".getBytes()
        );
        User operator = userRepository.findByEmailIgnoreCase("operator@travelbox.pe").orElseThrow();
        opsQrHandoffService.markStoredWithPhotos(
                reservationId,
                "Ingreso con fotos por bulto",
                java.util.List.of(warehousePhoto),
                AuthUserPrincipal.from(operator)
        );

        perform(mockMvc, post("/api/v1/ops/qr-handoff/reservations/{reservationId}/ready-for-pickup", reservationId)
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notes": "Listo para recojo"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pickupPinPreview").isNotEmpty());

        perform(mockMvc, get("/api/v1/reservations/{id}", reservationId)
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"))
                .andExpect(jsonPath("$.operationalDetail.pickupPinGenerated").value(true))
                .andExpect(jsonPath("$.operationalDetail.pickupPinVisible").value(true))
                .andExpect(jsonPath("$.operationalDetail.pickupPin").isNotEmpty())
                .andExpect(jsonPath("$.operationalDetail.canViewLuggagePhotos").value(true))
                .andExpect(jsonPath("$.operationalDetail.expectedLuggagePhotos").value(1))
                .andExpect(jsonPath("$.operationalDetail.storedLuggagePhotos").value(1))
                .andExpect(jsonPath("$.operationalDetail.luggagePhotos.length()").value(2))
                .andExpect(jsonPath("$.operationalDetail.luggagePhotos[0].type").value("CLIENT_HANDOFF_PHOTO"))
                .andExpect(jsonPath("$.operationalDetail.luggagePhotos[1].type").value("CHECKIN_BAG_PHOTO"));
    }

    private void moveReservationToOutForDelivery(String clientToken, String operatorToken, long reservationId) throws Exception {
        perform(mockMvc, post("/api/v1/inventory/checkin")
                        .header("Authorization", "Bearer " + operatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "notes":"Ingreso validado para delivery."
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationStatus").value("STORED"));

        perform(mockMvc, post("/api/v1/delivery-orders")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s,
                                  "type":"DELIVERY",
                                  "address":"Hotel Lima Centro",
                                  "zone":"LIMA"
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    private long createAndConfirmReservation(
            String clientToken,
            Long warehouseId,
            boolean pickupRequested,
            boolean dropoffRequested
    ) throws Exception {
        Instant startAt = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant endAt = startAt.plus(3, ChronoUnit.HOURS);
        MvcResult reservationResult = perform(mockMvc, post("/api/v1/reservations")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "warehouseId": %s,
                                  "startAt":"%s",
                                  "endAt":"%s",
                                  "estimatedItems":1,
                                  "pickupRequested": %s,
                                  "dropoffRequested": %s
                                }
                                """.formatted(warehouseId, startAt, endAt, pickupRequested, dropoffRequested)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode reservationJson = objectMapper.readTree(reservationResult.getResponse().getContentAsString());
        long reservationId = reservationJson.get("id").asLong();

        MvcResult intentResult = perform(mockMvc, post("/api/v1/payments/intents")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "reservationId": %s
                                }
                                """.formatted(reservationId)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode intentJson = objectMapper.readTree(intentResult.getResponse().getContentAsString());
        long paymentIntentId = intentJson.get("id").asLong();

        perform(mockMvc, post("/api/v1/payments/confirm")
                        .header("Authorization", "Bearer " + clientToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paymentIntentId": %s,
                                  "approved": true,
                                  "paymentMethod":"wallet"
                                }
                                """.formatted(paymentIntentId)))
                .andExpect(status().isOk());
        return reservationId;
    }

    private String loginAndGetAccessToken(String email, String password) throws Exception {
        MvcResult loginResult = perform(mockMvc, post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"%s",
                                  "password":"%s"
                                }
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginJson.get("accessToken").asText();
    }

    private Warehouse warehouseForScopedOps() {
        return warehouseRepository.findByActiveTrueOrderByNameAsc().stream()
                .filter(warehouse -> warehouse.getName().equals("TravelBox Miraflores")
                        || warehouse.getName().equals("TravelBox Barranco")
                        || warehouse.getName().equals("TravelBox Lima Centro"))
                .findFirst()
                .orElseThrow();
    }
}
