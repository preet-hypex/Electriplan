package com.hypex.electriplan.model.design;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hypex.electriplan.model.Checks;
import com.hypex.electriplan.model.units.Amperes;
import com.hypex.electriplan.model.units.Lumens;
import com.hypex.electriplan.model.units.Watts;

import lombok.Builder;
import lombok.Singular;
import lombok.With;
import org.jspecify.annotations.Nullable;

/** What a point is: which properties apply depends on its kind; the item code ties it to the bill of materials. */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder(toBuilder = true)
@With
public record PointSpec(
        @Nullable String itemCode,
        @Nullable Watts watts,
        @Nullable Lumens lumens,
        @Nullable IcRating ic,
        @Nullable String ip,
        @Nullable Amperes ratingA,
        @Nullable Integer gangs,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) @Singular List<SwitchWay> ways) {

    public static final Pattern IP = Pattern.compile("^IP[0-9X][0-9X]$");

    public PointSpec {
        Checks.optionalMatches(itemCode, Checks.ITEM_CODE, "itemCode");
        Checks.optionalMatches(ip, IP, "ip");
        if (ratingA != null) {
            Checks.positive(ratingA.value(), "ratingA");
        }
        if (gangs != null) {
            Checks.between(gangs, 1, 6, "gangs");
        }
        ways = Checks.optionalList(ways, "ways");
    }

    /** No properties: what a point carries before the catalogue fills it in. */
    public static PointSpec empty() {
        return builder().build();
    }
}
