package com.hypex.electriplan.tenancy.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.CompanyContext;
import com.hypex.electriplan.tenancy.service.TenantSession;

import jakarta.persistence.EntityManagerFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;

/**
 * The application's transaction manager: Hibernate's, plus row-level security.
 * When a transaction begins, it sets {@code electriplan.organisation_id} and
 * {@code electriplan.actor_id} for that transaction only ({@code set_config(...,
 * true)}, i.e. SET LOCAL), from the request's {@link TenantSession}. Every query
 * in it, Hibernate's or plain JDBC, is then limited to the request's company.
 * With no company the setting is empty, and the company tables return nothing:
 * isolation fails closed.
 *
 * <p>This is the one place that has to use plain SQL: Hibernate has no way to
 * set a Postgres session setting.
 */
public class TenantTransactionManager extends JpaTransactionManager {

    private static final String APPLY = """
            SELECT set_config('electriplan.organisation_id', ?, true),
                   set_config('electriplan.actor_id', ?, true)""";

    private final boolean applySettings;

    TenantTransactionManager(EntityManagerFactory factory, boolean applySettings) {
        super(factory);
        this.applySettings = applySettings;
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);
        if (applySettings) {
            apply(TenantSession.company(), TenantSession.actorId());
        }
    }

    private void apply(@Nullable CompanyContext company, @Nullable UUID actor) {
        // The connection Hibernate has just begun this transaction on.
        Connection connection = DataSourceUtils.getConnection(getDataSource());
        try (PreparedStatement statement = connection.prepareStatement(APPLY)) {
            statement.setString(1, company == null ? "" : company.organisationId().toString());
            statement.setString(2, actor == null ? "" : actor.toString());
            statement.execute();
        } catch (SQLException e) {
            throw new TransactionSystemException("Could not set the company for this transaction", e);
        }
    }
}
