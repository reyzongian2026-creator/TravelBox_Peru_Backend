package com.tuempresa.storage.firebase.application;

import com.tuempresa.storage.users.domain.User;
import com.tuempresa.storage.users.infrastructure.out.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class FirebaseUserMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirebaseUserMigrationRunner.class);

    private final UserRepository userRepository;
    private final FirebaseAdminService firebaseAdminService;
    private final boolean migrationEnabled;
    private final boolean failFast;

    public FirebaseUserMigrationRunner(
            UserRepository userRepository,
            FirebaseAdminService firebaseAdminService,
            @Value("${app.firebase.user-migration.enabled:true}") boolean migrationEnabled,
            @Value("${app.firebase.user-migration.fail-fast:false}") boolean failFast
    ) {
        this.userRepository = userRepository;
        this.firebaseAdminService = firebaseAdminService;
        this.migrationEnabled = migrationEnabled;
        this.failFast = failFast;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!migrationEnabled) {
            return;
        }
        if (!firebaseAdminService.isEnabled()) {
            log.info("Firebase user migration skipped: Firebase Admin is disabled.");
            return;
        }

        List<User> users = userRepository.findAll();
        int migrated = 0;
        int skipped = 0;
        int failed = 0;

        for (User user : users) {
            try {
                if (mustSkip(user)) {
                    skipped += 1;
                    continue;
                }

                String firebaseUid = firebaseAdminService.syncUserAccount(user, null);
                if (firebaseUid != null && !firebaseUid.equals(user.getFirebaseUid())) {
                    user.linkFirebaseIdentity(user.getAuthProvider(), firebaseUid);
                    user = userRepository.save(user);
                }
                firebaseAdminService.mirrorClientProfile(user);
                migrated += 1;
            } catch (RuntimeException ex) {
                failed += 1;
                log.error(
                        "Firebase user migration failed for email={} id={}: {}",
                        user.getEmail(),
                        user.getId(),
                        ex.getMessage()
                );
                if (failFast) {
                    throw ex;
                }
            }
        }

        log.info(
                "Firebase user migration finished. total={} migrated={} skipped={} failed={}",
                users.size(),
                migrated,
                skipped,
                failed
        );
    }

    private boolean mustSkip(User user) {
        if (user == null) {
            return true;
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return true;
        }
        return user.getFirebaseUid() != null && !user.getFirebaseUid().isBlank();
    }
}
