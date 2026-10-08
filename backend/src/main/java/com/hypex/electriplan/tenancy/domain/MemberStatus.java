package com.hypex.electriplan.tenancy.domain;

/** Whether a membership is in use. A suspended member keeps their place but cannot act in the company. */
public enum MemberStatus {
    ACTIVE, SUSPENDED;

    public String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
