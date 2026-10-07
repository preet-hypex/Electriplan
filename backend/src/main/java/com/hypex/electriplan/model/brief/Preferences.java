package com.hypex.electriplan.model.brief;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Millimetres;
import com.hypex.electriplan.model.units.Percent;

import lombok.Builder;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** The builder's overrides of design-policy defaults for this house. Never overrides a mandatory rule. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record Preferences(
        @Nullable String downlightItemCode,
        @Nullable Millimetres switchHeight,
        @Nullable Millimetres gpoHeight,
        @Nullable Percent spareSwitchboardPolesPct) {

    public Preferences {
        Checks.optionalMatches(downlightItemCode, Checks.ITEM_CODE, "downlightItemCode");
        if (switchHeight != null) {
            Checks.between(switchHeight.value(), 900, 1400, "switchHeight");
        }
        if (gpoHeight != null) {
            Checks.between(gpoHeight.value(), 200, 1400, "gpoHeight");
        }
    }
}
