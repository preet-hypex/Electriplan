package com.hypex.electriplan.users.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.hypex.electriplan.users.dao.SupabaseUserRepository;
import com.hypex.electriplan.users.entity.SupabaseUser;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** How people are named to others: their full name, or their email when they have not given one. */
@Service
@RequiredArgsConstructor
public class People {

    private final SupabaseUserRepository users;

    /** Names for the given people; anyone not (yet) copied from Supabase is left out. */
    public Map<UUID, String> displayNames(Collection<UUID> ids) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID id : ids) {
            users.find(id).map(People::nameOf).ifPresent(name -> names.put(id, name));
        }
        return names;
    }

    static String nameOf(SupabaseUser user) {
        if (user.userMetadata().get("full_name") instanceof String full && !full.isBlank()) {
            return full.strip();
        }
        return user.email() != null ? user.email() : "Someone";
    }
}
