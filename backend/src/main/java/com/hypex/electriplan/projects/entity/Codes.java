package com.hypex.electriplan.projects.entity;

import java.util.Arrays;

import com.hypex.electriplan.projects.domain.AnalysisStatus;
import com.hypex.electriplan.projects.domain.DwellingType;
import com.hypex.electriplan.projects.domain.FloorPlanOrigin;
import com.hypex.electriplan.projects.domain.FloorPlanState;
import com.hypex.electriplan.projects.domain.HouseStage;
import com.hypex.electriplan.projects.domain.ProjectStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** The projects enums as the lowercase codes the database's checks use. */
public final class Codes {

    private Codes() {
    }

    public static <E extends Enum<E>> E fromCode(Class<E> type, String code) {
        return Arrays.stream(type.getEnumConstants())
                .filter(e -> e.name().equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown " + type.getSimpleName() + " '" + code + "'"));
    }

    @Converter
    static final class Status implements AttributeConverter<ProjectStatus, String> {
        @Override public String convertToDatabaseColumn(ProjectStatus value) { return value.code(); }
        @Override public ProjectStatus convertToEntityAttribute(String code) { return fromCode(ProjectStatus.class, code); }
    }

    @Converter
    static final class Dwelling implements AttributeConverter<DwellingType, String> {
        @Override public String convertToDatabaseColumn(DwellingType value) { return value.code(); }
        @Override public DwellingType convertToEntityAttribute(String code) { return fromCode(DwellingType.class, code); }
    }

    @Converter
    static final class PlanState implements AttributeConverter<FloorPlanState, String> {
        @Override public String convertToDatabaseColumn(FloorPlanState value) { return value.code(); }
        @Override public FloorPlanState convertToEntityAttribute(String code) { return fromCode(FloorPlanState.class, code); }
    }

    @Converter
    static final class Origin implements AttributeConverter<FloorPlanOrigin, String> {
        @Override public String convertToDatabaseColumn(FloorPlanOrigin value) { return value.code(); }
        @Override public FloorPlanOrigin convertToEntityAttribute(String code) { return fromCode(FloorPlanOrigin.class, code); }
    }

    @Converter
    static final class RunStatus implements AttributeConverter<AnalysisStatus, String> {
        @Override public String convertToDatabaseColumn(AnalysisStatus value) { return value.code(); }
        @Override public AnalysisStatus convertToEntityAttribute(String code) { return fromCode(AnalysisStatus.class, code); }
    }

    @Converter
    static final class Stage implements AttributeConverter<HouseStage, String> {
        @Override public String convertToDatabaseColumn(HouseStage value) { return value.code(); }
        // A house's first stage event has no stage before it.
        @Override public HouseStage convertToEntityAttribute(String code) { return code == null ? null : fromCode(HouseStage.class, code); }
    }
}
