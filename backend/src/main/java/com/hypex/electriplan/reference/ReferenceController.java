package com.hypex.electriplan.reference;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reference lists for the UI's choices, e.g. the distributor dropdown in a project brief. */
@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
class ReferenceController {

    private final DistributorDirectory directory;

    /** All distributors, or only those in {@code state}. */
    @GetMapping("/distributors")
    List<Distributor> distributors(@RequestParam(required = false) @Nullable AustralianState state) {
        return state == null ? directory.all() : directory.inState(state);
    }
}
