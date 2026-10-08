package com.hypex.electriplan.tenancy.dto;

import java.util.Set;
import java.util.UUID;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;
import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.Permission;

public record Current(UUID id, String name, String slug, MemberRole role, LicenceStatus licence,
               Set<Permission> permissions) {
}
