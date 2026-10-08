package com.hypex.electriplan.reference.controller;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.dto.AddressSuggestion;
import com.hypex.electriplan.reference.dto.Distributor;
import com.hypex.electriplan.reference.dto.ErrorMessage;
import com.hypex.electriplan.reference.service.AddressFinder;
import com.hypex.electriplan.reference.service.DistributorDirectory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reference lists for the UI's choices, e.g. the distributor dropdown in a project brief. */
@RestController
@Tag(name = "Reference data", description = "Lists for the app's choices, such as distributors, and finding addresses.")
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final DistributorDirectory directory;
    private final AddressFinder addresses;

    /** All distributors, or only those in {@code state}. */
    @GetMapping("/distributors")
    @Operation(operationId = "listDistributors", summary = "Electricity distributors",
            description = "Every distributor, or only those in one state, for the project brief's distributor field.")
    List<Distributor> distributors(@Parameter(description = "Only distributors in this state.") @RequestParam(required = false) @Nullable AustralianState state) {
        return state == null ? directory.all() : directory.inState(state);
    }

    @GetMapping("/addresses")
    @Operation(operationId = "findAddresses", summary = "Find an Australian address",
            description = "Suggestions while typing a site address, from an OpenStreetMap geocoder (Photon). Fewer than 3 characters: none. "
                    + "House numbers are not always known: the person checks the suggestion.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "503", description = "Address search is off or its provider did not answer: type the address.",
            content = @Content(schema = @Schema(implementation = ErrorMessage.class)))
    List<AddressSuggestion> addresses(@Parameter(description = "What has been typed so far.") @RequestParam String q) {
        return addresses.find(q);
    }
}
