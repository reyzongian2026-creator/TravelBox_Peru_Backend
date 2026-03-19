package com.tuempresa.storage.profile.infrastructure.in.web;

import com.tuempresa.storage.profile.application.dto.OnboardingStatusResponse;
import com.tuempresa.storage.profile.application.dto.UpdateProfileRequest;
import com.tuempresa.storage.profile.application.dto.UserProfileResponse;
import com.tuempresa.storage.profile.application.usecase.ProfileService;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveBlockingExecutor;
import com.tuempresa.storage.shared.infrastructure.reactive.ReactiveMultipartAdapter;
import com.tuempresa.storage.shared.infrastructure.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final SecurityUtils securityUtils;
    private final ReactiveBlockingExecutor reactiveBlockingExecutor;
    private final ReactiveMultipartAdapter reactiveMultipartAdapter;

    public ProfileController(
            ProfileService profileService,
            SecurityUtils securityUtils,
            ReactiveBlockingExecutor reactiveBlockingExecutor,
            ReactiveMultipartAdapter reactiveMultipartAdapter
    ) {
        this.profileService = profileService;
        this.securityUtils = securityUtils;
        this.reactiveBlockingExecutor = reactiveBlockingExecutor;
        this.reactiveMultipartAdapter = reactiveMultipartAdapter;
    }

    @GetMapping("/me")
    public Mono<UserProfileResponse> me() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> profileService.myProfile(currentUser)
                ));
    }

    @GetMapping("/me/onboarding-status")
    public Mono<OnboardingStatusResponse> onboardingStatus() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> profileService.myOnboardingStatus(currentUser)
                ));
    }

    @PostMapping("/me/onboarding-complete")
    public Mono<OnboardingStatusResponse> completeOnboarding() {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> profileService.completeMyOnboarding(currentUser)
                ));
    }

    @PatchMapping("/me")
    public Mono<UserProfileResponse> update(@Valid @RequestBody UpdateProfileRequest request) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveBlockingExecutor.call(
                        () -> profileService.updateMyProfile(request, currentUser)
                ));
    }

    @PostMapping(value = "/me/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<UserProfileResponse> uploadPhoto(@RequestPart("file") FilePart file) {
        return securityUtils.currentUserOrThrowReactive()
                .flatMap(currentUser -> reactiveMultipartAdapter.toMultipartFile(file)
                        .flatMap(multipartFile -> reactiveBlockingExecutor.call(
                                () -> profileService.uploadMyProfilePhoto(multipartFile, currentUser)
                        )));
    }
}
