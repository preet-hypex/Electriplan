package com.hypex.electriplan.model.common;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Australian states and territories. Their codes are the same in JSON. */
public enum AustralianState {
    NSW("New South Wales"),
    VIC("Victoria"),
    QLD("Queensland"),
    WA("Western Australia"),
    SA("South Australia"),
    TAS("Tasmania"),
    ACT("Australian Capital Territory"),
    NT("Northern Territory");

    private final String fullName;

    AustralianState(String fullName) {
        this.fullName = fullName;
    }

    /** "Victoria": the name addresses and maps use. */
    public String fullName() {
        return fullName;
    }

    /** The state with this full name, in any case ("victoria"), if it is one. */
    public static Optional<AustralianState> byFullName(String name) {
        String wanted = name.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.fullName.toLowerCase(Locale.ROOT).equals(wanted)).findFirst();
    }
}
