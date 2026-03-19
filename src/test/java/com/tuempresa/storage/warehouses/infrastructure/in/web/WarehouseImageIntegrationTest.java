package com.tuempresa.storage.warehouses.infrastructure.in.web;

import com.tuempresa.storage.warehouses.domain.Warehouse;
import com.tuempresa.storage.warehouses.infrastructure.out.persistence.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.tuempresa.storage.support.MockMvcReactiveSupport.perform;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WarehouseImageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @Test
    void shouldReturnImageWithoutLazyInitializationError() throws Exception {
        Warehouse warehouse = warehouseRepository.findByActiveTrueOrderByNameAsc()
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No hay almacenes activos para probar imagen."));

        perform(mockMvc, get("/api/v1/warehouses/{id}/image", warehouse.getId()))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String contentType = result.getResponse().getContentType();
                    assertTrue(
                            contentType != null && MediaType.parseMediaType(contentType).isCompatibleWith(MediaType.IMAGE_PNG),
                            "El endpoint debe responder imagen/png compatible."
                    );
                    assertTrue(
                            result.getResponse().getContentAsByteArray().length > 0,
                            "El endpoint debe devolver bytes de imagen."
                    );
                });
    }
}
