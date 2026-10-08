package com.hypex.electriplan.tenancy;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * What a person may do in a company. Endpoints ask for one with
 * {@link RequiresPermission}; {@link PermissionMatrix} says which roles have
 * it. The codes are what the web app receives, to show or hide actions.
 */
public enum Permission {
    COMPANY_VIEW("company.view", "View projects, plans, designs, quotes"),
    PROJECT_EDIT("project.edit", "Create / edit projects and clients"),
    FLOOR_PLAN_EDIT("floor-plan.edit", "Upload and edit floor plans, fixtures, briefs"),
    DESIGN_EDIT("design.edit", "Run the engine; edit electrical designs"),
    RULE_OVERRIDE("rule.override", "Override a **mandatory** rule (with a recorded reason)"),
    REVIEW_REQUEST("review.request", "Request a review"),
    DESIGN_SIGN_OFF("design.sign-off", "**Approve / sign off a design**"),
    QUOTE_EDIT("quote.edit", "Create and send quotes"),
    MEMBER_MANAGE("member.manage", "Invite members, change roles, suspend, remove"),
    COMPANY_EDIT("company.edit", "Edit company details"),
    LICENCE_VIEW("licence.view", "See the licence and seat use"),
    OWNERSHIP_TRANSFER("ownership.transfer", "Transfer ownership; close the company");

    private final String code;
    private final String description;

    Permission(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @JsonValue
    public String code() {
        return code;
    }

    /** What it allows, as a person would say it (markdown, for the plan's table). */
    public String description() {
        return description;
    }

    /**
     * Also needs a verified electrical licence in the project's state, whatever
     * the role (checked where the action happens, story T5).
     */
    public boolean needsLicence() {
        return this == RULE_OVERRIDE || this == DESIGN_SIGN_OFF;
    }
}
