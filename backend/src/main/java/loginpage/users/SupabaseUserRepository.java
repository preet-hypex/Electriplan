package loginpage.users;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class SupabaseUserRepository {

    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {};

    private static final String COPIED = """
            email, phone, user_metadata, app_metadata, email_confirmed_at, invited_at,
            last_sign_in_at, banned_until, created_at, updated_at""";

    private static final String COLUMNS = "id, " + COPIED;

    private static final String UPSERT = """
            insert into app.supabase_user (%1$s)
            values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?, ?)
            on conflict (id) do update
               set (%2$s, copied_at) = (%3$s, now())
             where (%4$s) is distinct from (%3$s)
            """.formatted(COLUMNS, COPIED, prefixed("excluded", COPIED), prefixed("supabase_user", COPIED));

    private final JdbcClient db;
    private final ObjectMapper json;

    SupabaseUserRepository(JdbcClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    boolean upsert(AdminUser user) {
        return db.sql(UPSERT)
                .params(user.id(), user.email(), user.phone(),
                        write(user.userMetadata()), write(user.appMetadata()),
                        timestamp(user.emailConfirmedAt()), timestamp(user.invitedAt()),
                        timestamp(user.lastSignInAt()), timestamp(user.bannedUntil()),
                        timestamp(user.createdAt()), timestamp(user.updatedAt()))
                .update() > 0;
    }

    boolean exists(UUID id) {
        return db.sql("select exists (select 1 from app.supabase_user where id = ?)")
                .param(id)
                .query(Boolean.class)
                .single();
    }

    Optional<SupabaseUser> find(UUID id) {
        return db.sql("select " + COLUMNS + ", copied_at from app.supabase_user where id = ?")
                .param(id)
                .query(this::toUser)
                .optional();
    }

    List<UUID> ids() {
        return db.sql("select id from app.supabase_user").query(UUID.class).list();
    }

    boolean delete(UUID id) {
        return db.sql("delete from app.supabase_user where id = ?").param(id).update() > 0;
    }

    private SupabaseUser toUser(ResultSet rs, int rowNum) throws SQLException {
        return new SupabaseUser(
                rs.getObject("id", UUID.class),
                rs.getString("email"),
                rs.getString("phone"),
                read(rs.getString("user_metadata")),
                read(rs.getString("app_metadata")),
                instant(rs, "email_confirmed_at"),
                instant(rs, "invited_at"),
                instant(rs, "last_sign_in_at"),
                instant(rs, "banned_until"),
                required(instant(rs, "created_at")),
                instant(rs, "updated_at"),
                required(instant(rs, "copied_at")));
    }

    private static String prefixed(String table, String columns) {
        return Arrays.stream(columns.split(","))
                .map(column -> table + "." + column.strip())
                .collect(Collectors.joining(", "));
    }

    private String write(@Nullable Map<String, Object> value) {
        try {
            return json.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Supabase sent metadata that is not JSON", e);
        }
    }

    private Map<String, Object> read(@Nullable String value) {
        try {
            return value == null ? Map.of() : json.readValue(value, JSON_OBJECT);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("app.supabase_user holds metadata that is not a JSON object", e);
        }
    }

    private static @Nullable OffsetDateTime timestamp(@Nullable Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static @Nullable Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Instant required(@Nullable Instant value) {
        if (value == null) {
            throw new IllegalStateException("app.supabase_user has a row without a timestamp it requires");
        }
        return value;
    }
}
