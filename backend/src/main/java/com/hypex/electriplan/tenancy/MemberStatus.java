package com.hypex.electriplan.tenancy;

/** Whether a membership is in use. A suspended member keeps their place but cannot act in the company. */
enum MemberStatus {
    ACTIVE, SUSPENDED;

    String code() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
