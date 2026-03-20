package com.tuempresa.storage.reservations.application.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateAssistedReservationRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void shouldRejectInvalidCustomerPhoneAndUnsupportedLanguage() {
        CreateAssistedReservationRequest request = buildRequest(
                "999999999",
                "jp"
        );

        Set<ConstraintViolation<CreateAssistedReservationRequest>> violations = validator.validate(request);

        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("customerPhone", "customerPreferredLanguage");
    }

    @Test
    void shouldAcceptE164PhoneAndSupportedLanguage() {
        CreateAssistedReservationRequest request = buildRequest(
                "+51999999999",
                "en"
        );

        Set<ConstraintViolation<CreateAssistedReservationRequest>> violations = validator.validate(request);

        assertThat(violations).isEmpty();
    }

    private CreateAssistedReservationRequest buildRequest(String phone, String language) {
        Instant startAt = Instant.now().plus(2, ChronoUnit.DAYS);
        Instant endAt = startAt.plus(2, ChronoUnit.HOURS);
        return new CreateAssistedReservationRequest(
                10L,
                startAt,
                endAt,
                2,
                "M",
                false,
                true,
                true,
                false,
                "Cliente Demo",
                "cliente.demo@test.com",
                phone,
                "Peru",
                language
        );
    }
}
