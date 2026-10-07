package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.common.WallAnchor;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** The switchboard: where it is, its main switch, its devices, surge protection and pole count. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Switchboard(
        WallAnchor location,
        MainSwitch mainSwitch,
        @Singular List<ProtectiveDevice> devices,
        @Nullable SurgeProtection spd,
        Integer polesUsed,
        Integer polesTotal) {

    public Switchboard {
        Checks.required(location, "location");
        Checks.required(mainSwitch, "mainSwitch");
        devices = Checks.list(devices, "devices");
        Checks.between(polesUsed, 0, Integer.MAX_VALUE, "polesUsed");
        Checks.between(polesTotal, 0, Integer.MAX_VALUE, "polesTotal");
    }
}
