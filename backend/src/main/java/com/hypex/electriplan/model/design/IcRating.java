package com.hypex.electriplan.model.design;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Recessed luminaire insulation-contact rating. IC-4 may be covered by insulation. */
public enum IcRating {
    @JsonProperty("IC-4") IC_4,
    @JsonProperty("IC") IC,
    @JsonProperty("non-IC") NON_IC
}
