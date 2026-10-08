package com.hypex.electriplan.reference.controller;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.dto.Distributor;
import com.hypex.electriplan.reference.service.DistributorDirectory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reference lists for the UI's choices, e.g. the distributor dropdown in a project brief. */
@RestController
@Tag(name = "Reference data", description = "Lists for the app's choices, such as distributors.")
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final DistributorDirectory directory;

    /** All distributors, or only those in {@code state}. */
    @GetMapping("/distributors")
    @Operation(operationId = "listDistributors", summary = "Electricity distributors",
            description = "Every distributor, or only those in one state, for the project brief's distributor field.")
    List<Distributor> distributors(@Parameter(description = "Only distributors in this state.") @RequestParam(required = false) @Nullable AustralianState state) {
        return state == null ? directory.all() : directory.inState(state);
    }
}
