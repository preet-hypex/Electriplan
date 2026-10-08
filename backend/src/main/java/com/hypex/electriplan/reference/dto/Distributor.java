package com.hypex.electriplan.reference.dto;

import com.hypex.electriplan.model.brief.DistributorCode;
import com.hypex.electriplan.model.common.AustralianState;

/** An electricity distributor (DNSP) as other modules and the API see it: its code, name and state. */
public record Distributor(DistributorCode code, String name, AustralianState state) {
}
