package com.hypex.electriplan.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The role matrix, checked cell by cell against the table below. Changing what
 * a role may do means changing both: a deliberate, reviewed decision.
 */
class PermissionMatrixTest {

    //                                    owner  admin  builder electrician viewer
    private static final Object[][] EXPECTED = {
            {Permission.COMPANY_VIEW,          true,  true,  true,  true,  true},
            {Permission.PROJECT_EDIT,          true,  true,  true,  false, false},
            {Permission.FLOOR_PLAN_EDIT,       true,  true,  true,  true,  false},
            {Permission.DESIGN_EDIT,           true,  true,  true,  true,  false},
            {Permission.RULE_OVERRIDE,         false, false, false, true,  false},
            {Permission.REVIEW_REQUEST,        true,  true,  true,  true,  false},
            {Permission.DESIGN_SIGN_OFF,       true,  true,  false, true,  false},
            {Permission.QUOTE_EDIT,            true,  true,  true,  false, false},
            {Permission.MEMBER_MANAGE,         true,  true,  false, false, false},
            {Permission.COMPANY_EDIT,          true,  true,  false, false, false},
            {Permission.LICENCE_VIEW,          true,  true,  false, false, false},
            {Permission.OWNERSHIP_TRANSFER,    true,  false, false, false, false},
    };

    static Stream<Arguments> cells() {
        Stream.Builder<Arguments> cells = Stream.builder();
        for (Object[] row : EXPECTED) {
            for (MemberRole role : MemberRole.values()) {
                cells.add(Arguments.of(role, row[0], row[1 + role.ordinal()]));
            }
        }
        return cells.build();
    }

    @ParameterizedTest(name = "{0} {1}: {2}")
    @MethodSource("cells")
    void everyRoleAndPermission(MemberRole role, Permission permission, boolean allowed) {
        assertThat(PermissionMatrix.allows(role, permission)).isEqualTo(allowed);
    }

    @Test
    void theTableCoversEveryPermission() {
        assertThat(Stream.of(EXPECTED).map(row -> row[0])).containsExactly((Object[]) Permission.values());
    }

    @Test
    void ownersDoEverythingButOverrideRulesViewersOnlyLook() {
        assertThat(PermissionMatrix.permissions(MemberRole.OWNER))
                .isEqualTo(EnumSet.complementOf(EnumSet.of(Permission.RULE_OVERRIDE)));
        assertThat(PermissionMatrix.permissions(MemberRole.VIEWER)).containsExactly(Permission.COMPANY_VIEW);
        assertThat(PermissionMatrix.rolesWith(Permission.RULE_OVERRIDE)).containsExactly(MemberRole.ELECTRICIAN);
        assertThat(PermissionMatrix.rolesWith(Permission.OWNERSHIP_TRANSFER)).containsExactly(MemberRole.OWNER);
    }

    @Test
    void signingOffAndOverridingAlsoNeedALicence() {
        assertThat(Stream.of(Permission.values()).filter(Permission::needsLicence))
                .containsExactly(Permission.RULE_OVERRIDE, Permission.DESIGN_SIGN_OFF);
    }

    @Test
    void permissionsTravelAsTheirCodes() throws Exception {
        assertThat(new ObjectMapper().writeValueAsString(EnumSet.of(Permission.LICENCE_VIEW, Permission.DESIGN_SIGN_OFF)))
                .isEqualTo("[\"design.sign-off\",\"licence.view\"]");
        assertThat(Stream.of(Permission.values()).map(Permission::code).distinct()).hasSize(Permission.values().length);
    }

    @Test
    void thePlanShowsExactlyThisMatrix() throws Exception {
        Path plan = Path.of("../documents/teams-and-licences-plan.md");
        String doc = Files.readString(plan);
        String start = "<!-- permission-matrix:start -->\n";
        String end = "<!-- permission-matrix:end -->";
        String table = doc.substring(doc.indexOf(start) + start.length(), doc.indexOf(end));
        assertThat(table)
                .as("documents/teams-and-licences-plan.md is out of date with PermissionMatrix; replace the table with:%n%s",
                        PermissionMatrix.markdown())
                .isEqualTo(PermissionMatrix.markdown());
    }
}
