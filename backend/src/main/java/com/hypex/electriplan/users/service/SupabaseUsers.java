package com.hypex.electriplan.users.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.hypex.electriplan.users.dao.SupabaseUserRepository;
import com.hypex.electriplan.users.dto.AdminUser;
import com.hypex.electriplan.users.entity.SupabaseUser;

import org.springframework.stereotype.Service;

@Service
public class SupabaseUsers {

    public static final int PAGE_SIZE = 500;

    private final SupabaseAdminApi supabase;
    private final SupabaseUserRepository copies;

    public SupabaseUsers(SupabaseAdminApi supabase, SupabaseUserRepository copies) {
        this.supabase = supabase;
        this.copies = copies;
    }

    public Optional<SupabaseUser> find(UUID id) {
        return copies.find(id);
    }

    public void copyIfMissing(UUID id) {
        if (!copies.exists(id)) {
            copy(id);
        }
    }

    public Optional<SupabaseUser> copy(UUID id) {
        Optional<AdminUser> user = supabase.find(id);
        if (user.isPresent()) {
            copies.upsert(user.get());
        } else {
            copies.delete(id);
        }
        return copies.find(id);
    }

    public Sync copyAll() {
        Set<UUID> listed = new HashSet<>();
        int changed = 0;
        for (int page = 1; ; page++) {
            List<AdminUser> users = supabase.page(page, PAGE_SIZE);
            int before = listed.size();
            for (AdminUser user : users) {
                if (listed.add(user.id()) && copies.upsert(user)) {
                    changed++;
                }
            }
            if (listed.size() == before) {
                break;
            }
        }

        int removed = 0;
        for (UUID id : copies.ids()) {
            if (listed.contains(id)) {
                continue;
            }
            Optional<AdminUser> user = supabase.find(id);
            if (user.isEmpty()) {
                removed += copies.delete(id) ? 1 : 0;
            } else if (copies.upsert(user.get())) {
                changed++;
            }
        }
        return new Sync(listed.size(), changed, removed);
    }

    public record Sync(int listed, int changed, int removed) {
    }
}
