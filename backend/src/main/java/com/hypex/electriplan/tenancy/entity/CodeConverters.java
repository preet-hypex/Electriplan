package com.hypex.electriplan.tenancy.entity;

import java.util.Arrays;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;
import com.hypex.electriplan.tenancy.domain.MemberRole;
import com.hypex.electriplan.tenancy.domain.MemberStatus;
import com.hypex.electriplan.tenancy.dto.Licence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Store the tenancy enums as the lowercase codes the database's CHECK constraints use. */
public final class CodeConverters {

    private CodeConverters() {
    }

    private static <E extends Enum<E>> E fromCode(Class<E> type, String code) {
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown " + type.getSimpleName() + " '" + code + "'"));
    }

    @Converter
    static final class Role implements AttributeConverter<MemberRole, String> {
        @Override public String convertToDatabaseColumn(MemberRole value) { return value.code(); }
        @Override public MemberRole convertToEntityAttribute(String code) { return fromCode(MemberRole.class, code); }
    }

    @Converter
    static final class Licence implements AttributeConverter<LicenceStatus, String> {
        @Override public String convertToDatabaseColumn(LicenceStatus value) { return value.code(); }
        @Override public LicenceStatus convertToEntityAttribute(String code) { return fromCode(LicenceStatus.class, code); }
    }

    @Converter
    static final class Status implements AttributeConverter<MemberStatus, String> {
        @Override public String convertToDatabaseColumn(MemberStatus value) { return value.code(); }
        @Override public MemberStatus convertToEntityAttribute(String code) { return fromCode(MemberStatus.class, code); }
    }
}
