package com.tuempresa.storage.profile.application.dto;

public record OnboardingStatusResponse(
        Long userId,
        boolean completed
) {
}
