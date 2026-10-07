package com.hypex.electriplan.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupabaseUsersTests {

    private final SupabaseAdminApi supabase = mock(SupabaseAdminApi.class);
    private final SupabaseUserRepository copies = mock(SupabaseUserRepository.class);
    private final SupabaseUsers users = new SupabaseUsers(supabase, copies);

    private static AdminUser user(int n) {
        return new AdminUser(new UUID(0, n), "user" + n + "@example.com", "", null, null,
                null, null, null, null, Instant.parse("2026-01-01T00:00:00Z"), null);
    }

    @Test
    void copiesEveryPageUntilSupabaseRunsOutOfUsers() {
        given(supabase.page(1, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(1), user(2)));
        given(supabase.page(2, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(3)));
        given(supabase.page(3, SupabaseUsers.PAGE_SIZE)).willReturn(List.of());
        given(copies.upsert(any())).willReturn(true);

        SupabaseUsers.Sync sync = users.copyAll();

        assertThat(sync).isEqualTo(new SupabaseUsers.Sync(3, 3, 0));
        verify(copies).upsert(user(3));
    }

    @Test
    void stopsWhenAPageHasNobodyNew() {
        given(supabase.page(1, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(1)));
        given(supabase.page(2, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(1)));

        assertThat(users.copyAll().listed()).isEqualTo(1);
        verify(supabase, never()).page(3, SupabaseUsers.PAGE_SIZE);
    }

    @Test
    void removesACopyOnlyOnceSupabaseConfirmsTheUserIsGone() {
        UUID deleted = new UUID(0, 8);
        UUID missedByTheListing = new UUID(0, 9);
        given(supabase.page(1, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(1)));
        given(copies.ids()).willReturn(List.of(user(1).id(), deleted, missedByTheListing));
        given(supabase.find(deleted)).willReturn(Optional.empty());
        given(supabase.find(missedByTheListing)).willReturn(Optional.of(user(9)));
        given(copies.delete(deleted)).willReturn(true);

        assertThat(users.copyAll().removed()).isEqualTo(1);
        verify(copies).delete(deleted);
        verify(copies, never()).delete(missedByTheListing);
        verify(copies).upsert(user(9));
    }

    @Test
    void aFailedListingRemovesNobody() {
        given(supabase.page(1, SupabaseUsers.PAGE_SIZE)).willReturn(List.of(user(1)));
        given(supabase.page(2, SupabaseUsers.PAGE_SIZE)).willThrow(new IllegalStateException("Supabase is down"));

        assertThatThrownBy(users::copyAll).hasMessageContaining("Supabase is down");
        verify(copies, never()).delete(any());
    }

    @Test
    void copiesACallerOnlyWhenTheCopyDoesNotHaveThem() {
        UUID known = user(1).id();
        UUID unknown = user(2).id();
        given(copies.exists(known)).willReturn(true);
        given(supabase.find(unknown)).willReturn(Optional.of(user(2)));

        users.copyIfMissing(known);
        users.copyIfMissing(unknown);

        verify(supabase, never()).find(known);
        verify(copies).upsert(user(2));
    }
}
