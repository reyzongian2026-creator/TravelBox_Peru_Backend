package com.tuempresa.storage.auth.application.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailChangeVerifyRequest(
        @NotBlank(message = "El correo es requerido.")
        String email,
        
        @NotBlank(message = "El codigo de verificacion es requerido.")
        String verificationCode
) {
}
