package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequest(
        @NotBlank @Size(min = 4, max = 20) String code,
        @Email @Size(max = 160) String email
) {
}
