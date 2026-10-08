package com.hypex.electriplan.tenancy.dto;

import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;
import com.hypex.electriplan.tenancy.domain.MemberRole;

public record Membership(UUID id, String name, MemberRole role, LicenceStatus licence) {
}
