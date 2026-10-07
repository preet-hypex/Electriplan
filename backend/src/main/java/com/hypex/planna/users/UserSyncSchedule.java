package com.hypex.planna.users;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.users.sync-enabled", havingValue = "true", matchIfMissing = true)
class UserSyncSchedule {

    private static final Logger log = LoggerFactory.getLogger(UserSyncSchedule.class);

    private final SupabaseUsers users;

    UserSyncSchedule(SupabaseUsers users) {
        this.users = users;
    }

    @Scheduled(fixedDelayString = "${app.users.sync-interval:PT5M}")
    void copyAll() {
        try {
            SupabaseUsers.Sync sync = users.copyAll();
            if (sync.changed() > 0 || sync.removed() > 0) {
                log.info("Copied {} Supabase users into plannasaas.supabase_user: {} changed, {} removed",
                        sync.listed(), sync.changed(), sync.removed());
            } else {
                log.debug("plannasaas.supabase_user already matches Supabase's {} users", sync.listed());
            }
        } catch (RuntimeException e) {
            log.warn("Could not copy users from Supabase; the copy is unchanged until the next run: {}", e.getMessage());
        }
    }
}
