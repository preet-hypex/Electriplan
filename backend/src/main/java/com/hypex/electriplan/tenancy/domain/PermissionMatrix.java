package com.hypex.electriplan.tenancy.domain;

import static com.hypex.electriplan.tenancy.domain.MemberRole.ADMIN;
import static com.hypex.electriplan.tenancy.domain.MemberRole.BUILDER;
import static com.hypex.electriplan.tenancy.domain.MemberRole.ELECTRICIAN;
import static com.hypex.electriplan.tenancy.domain.MemberRole.OWNER;
import static com.hypex.electriplan.tenancy.domain.MemberRole.VIEWER;
import static com.hypex.electriplan.tenancy.domain.Permission.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which role may do what: the one place this is decided. Changing a role's
 * permissions is a reviewed code change, checked by PermissionMatrixTest
 * against an explicit table, and the plan's table is generated from here.
 *
 * <p>Rules that hold whatever the role (nobody changes their own role, owners
 * are managed only by owners, a company keeps an owner) are enforced by the
 * database (migration V6), not here.
 */
public final class PermissionMatrix {

    private static final Map<MemberRole, Set<Permission>> ALLOWED = new EnumMap<>(MemberRole.class);

    static {
        ALLOWED.put(OWNER, EnumSet.complementOf(EnumSet.of(RULE_OVERRIDE)));
        ALLOWED.put(ADMIN, EnumSet.of(COMPANY_VIEW, PROJECT_EDIT, FLOOR_PLAN_EDIT, DESIGN_EDIT, REVIEW_REQUEST,
                DESIGN_SIGN_OFF, QUOTE_EDIT, MEMBER_MANAGE, COMPANY_EDIT, LICENCE_VIEW));
        ALLOWED.put(BUILDER, EnumSet.of(COMPANY_VIEW, PROJECT_EDIT, FLOOR_PLAN_EDIT, DESIGN_EDIT, REVIEW_REQUEST, QUOTE_EDIT));
        ALLOWED.put(ELECTRICIAN, EnumSet.of(COMPANY_VIEW, FLOOR_PLAN_EDIT, DESIGN_EDIT, RULE_OVERRIDE, REVIEW_REQUEST,
                DESIGN_SIGN_OFF));
        ALLOWED.put(VIEWER, EnumSet.of(COMPANY_VIEW));
        ALLOWED.replaceAll((role, permissions) -> Collections.unmodifiableSet(permissions));
    }

    private PermissionMatrix() {
    }

    public static boolean allows(MemberRole role, Permission permission) {
        return ALLOWED.get(role).contains(permission);
    }

    /** Everything a role may do, in declaration order. */
    public static Set<Permission> permissions(MemberRole role) {
        return ALLOWED.get(role);
    }

    /** The roles that have a permission, in declaration order. */
    public static Set<MemberRole> rolesWith(Permission permission) {
        return Arrays.stream(MemberRole.values()).filter(r -> allows(r, permission))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(MemberRole.class)));
    }

    /**
     * The matrix as the markdown table in documents/teams-and-licences-plan.md.
     * ¹ marks permissions that also need a verified licence; ² an admin's
     * limit on managing members (they cannot manage owners).
     */
    public static String markdown() {
        StringBuilder out = new StringBuilder("| Permission | Owner | Admin | Builder | Electrician | Viewer |\n");
        out.append("|---|:-:|:-:|:-:|:-:|:-:|\n");
        for (Permission p : Permission.values()) {
            out.append("| ").append(p.description()).append(" `").append(p.code()).append('`');
            for (MemberRole role : MemberRole.values()) {
                out.append(" | ");
                if (allows(role, p)) {
                    out.append('✓');
                    if (p.needsLicence()) {
                        out.append(" ¹");
                    } else if (p == MEMBER_MANAGE && role == ADMIN) {
                        out.append(" ²");
                    }
                }
            }
            out.append(" |\n");
        }
        return out.toString();
    }
}
