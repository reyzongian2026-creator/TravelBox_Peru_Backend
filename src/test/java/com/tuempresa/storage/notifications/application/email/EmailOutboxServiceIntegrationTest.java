package com.tuempresa.storage.notifications.application.email;

import com.tuempresa.storage.notifications.infrastructure.out.persistence.EmailOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("test")
class EmailOutboxServiceIntegrationTest {

    @Autowired
    private EmailOutboxService emailOutboxService;

    @Autowired
    private EmailOutboxRepository emailOutboxRepository;

    @BeforeEach
    void setUp() {
        emailOutboxRepository.deleteAll();
    }

    @Test
    void shouldPersistOnlyOneRowWhenDedupKeyIsRepeated() {
        String dedupKey = "test-dedup-key-001";

        boolean firstQueued = emailOutboxService.enqueue(
                "cliente@test.com",
                "Asunto de prueba",
                "<p>Hola</p>",
                "Hola",
                "TEST_EVENT",
                dedupKey
        );
        boolean secondQueued = emailOutboxService.enqueue(
                "cliente@test.com",
                "Asunto de prueba",
                "<p>Hola</p>",
                "Hola",
                "TEST_EVENT",
                dedupKey
        );

        assertThat(firstQueued).isTrue();
        assertThat(secondQueued).isFalse();
        assertThat(emailOutboxRepository.count()).isEqualTo(1);
    }
}
