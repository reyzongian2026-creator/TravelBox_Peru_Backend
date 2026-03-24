package com.tuempresa.storage.firebase.application;

import com.tuempresa.storage.users.domain.AuthProvider;

public record FirebaseClientIdentity(
        String uid,
        String email,
        String displayName,
        String photoUrl,
        AuthProvider authProvider
) {
}
