package com.hypex.electriplan.reference.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.config.AddressFinderConfiguration;
import com.hypex.electriplan.reference.domain.AddressSearchUnavailableException;
import com.hypex.electriplan.reference.dto.AddressSuggestion;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Suggests Australian addresses while someone types a site address, from a
 * Photon geocoder (OpenStreetMap data). Which Photon is a setting
 * (electriplan.addresses.url): the public one by default, our own in
 * production, or another provider behind the same method later (e.g. G-NAF).
 *
 * <p>OpenStreetMap does not have every house number, so a suggestion fills in
 * the street, suburb, state and postcode, and the person checks the number.
 */
@Service
public class AddressFinder {

    public static final int MIN_CHARACTERS = 3;
    public static final int MAX_SUGGESTIONS = 8;

    /** Mainland Australia and Tasmania, as west, south, east, north. */
    static final String AUSTRALIA = "112.9,-43.7,153.7,-10.6";

    private final RestClient photon;
    private final boolean enabled;

    public AddressFinder(@Qualifier(AddressFinderConfiguration.CLIENT) RestClient photon,
                         @Value("${electriplan.addresses.enabled:true}") boolean enabled) {
        this.photon = photon;
        this.enabled = enabled;
    }

    /**
     * Up to {@value #MAX_SUGGESTIONS} addresses matching what was typed, best
     * first. Fewer than {@value #MIN_CHARACTERS} characters: none.
     *
     * @throws AddressSearchUnavailableException when turned off, or the provider fails or is slow
     */
    public List<AddressSuggestion> find(String typed) {
        String text = typed == null ? "" : typed.strip();
        if (text.length() < MIN_CHARACTERS) {
            return List.of();
        }
        if (!enabled) {
            throw new AddressSearchUnavailableException("Address search is turned off. Type the address instead.");
        }
        JsonNode found = ask(text);

        // Several map features can share one address (a building and its entrance): keep the first.
        Map<String, AddressSuggestion> byLabel = new LinkedHashMap<>();
        for (JsonNode feature : found.path("features")) {
            suggestion(feature).ifPresent(s -> byLabel.putIfAbsent(s.label(), s));
            if (byLabel.size() == MAX_SUGGESTIONS) {
                break;
            }
        }
        return new ArrayList<>(byLabel.values());
    }

    private JsonNode ask(String text) {
        try {
            JsonNode body = photon.get()
                    .uri(uri -> uri.path("/api/")
                            .queryParam("q", text)
                            .queryParam("limit", MAX_SUGGESTIONS * 2)
                            .queryParam("lang", "en")
                            .queryParam("bbox", AUSTRALIA)
                            .queryParam("layer", "house")
                            .queryParam("layer", "street")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new AddressSearchUnavailableException("The address finder gave no answer. Type the address instead.");
            }
            return body;
        } catch (RestClientException e) {
            throw new AddressSearchUnavailableException(
                    "Address search is not available right now. Type the address instead.", e);
        }
    }

    /** A Photon feature as a suggestion, if it is an Australian address in a state we know. */
    static Optional<AddressSuggestion> suggestion(JsonNode feature) {
        JsonNode p = feature.path("properties");
        if (!"AU".equalsIgnoreCase(text(p, "countrycode"))) {
            return Optional.empty();
        }
        String stateName = text(p, "state");
        AustralianState state = stateName == null ? null : AustralianState.byFullName(stateName).orElse(null);
        if (state == null) {
            return Optional.empty();
        }
        String street = street(p);
        String suburb = firstOf(text(p, "district"), text(p, "locality"), text(p, "city"));
        String postcode = text(p, "postcode");
        if (street == null && suburb == null) {
            return Optional.empty();
        }

        StringBuilder label = new StringBuilder();
        if (street != null) {
            label.append(street);
        }
        if (suburb != null) {
            label.append(label.isEmpty() ? "" : ", ").append(suburb);
        }
        label.append(' ').append(state);
        if (postcode != null) {
            label.append(' ').append(postcode);
        }

        JsonNode point = feature.path("geometry").path("coordinates");
        return Optional.of(new AddressSuggestion(label.toString().strip(), street, suburb, state, postcode,
                point.path(1).asDouble(), point.path(0).asDouble()));
    }

    /** "12 Glenlyon Road"; just the street when the number is not known; a street's own name for a street. */
    private static @Nullable String street(JsonNode p) {
        String number = text(p, "housenumber");
        String street = "street".equals(text(p, "type")) ? firstOf(text(p, "name"), text(p, "street")) : text(p, "street");
        if (street == null) {
            return null;
        }
        return number == null ? street : number + " " + street;
    }

    private static @Nullable String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isTextual() || value.asText().isBlank() ? null : value.asText().strip();
    }

    private static @Nullable String firstOf(@Nullable String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
