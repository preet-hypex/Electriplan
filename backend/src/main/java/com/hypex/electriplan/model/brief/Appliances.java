package com.hypex.electriplan.model.brief;

import com.hypex.electriplan.model.Checks;

import lombok.Builder;
import lombok.With;

/** The appliances that decide dedicated circuits and maximum demand. */
@Builder(toBuilder = true)
@With
public record Appliances(
        CooktopType cooktop,
        OvenType oven,
        HotWaterType hotWater,
        AirConditioningType airConditioning,
        Boolean evCharger,
        Boolean pool) {

    public Appliances {
        Checks.required(cooktop, "cooktop");
        Checks.required(oven, "oven");
        Checks.required(hotWater, "hotWater");
        Checks.required(airConditioning, "airConditioning");
        Checks.required(evCharger, "evCharger");
        Checks.required(pool, "pool");
    }
}
