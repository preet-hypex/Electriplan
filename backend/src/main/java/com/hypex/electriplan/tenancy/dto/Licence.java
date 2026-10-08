package com.hypex.electriplan.tenancy.dto;

import java.time.LocalDate;

import com.hypex.electriplan.tenancy.domain.LicenceStatus;

import org.jspecify.annotations.Nullable;

public record Licence(LicenceStatus status, int seatLimit, long seatsInUse, LocalDate licenceStartsOn,
               @Nullable LocalDate licenceEndsOn) {
}
