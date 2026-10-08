package com.hypex.electriplan.reference.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import com.hypex.electriplan.model.common.AustralianState;
import com.hypex.electriplan.reference.domain.AddressSearchUnavailableException;
import com.hypex.electriplan.reference.dto.AddressSuggestion;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** The address finder against a pretend Photon server, with answers shaped like the real one's. */
class AddressFinderTest {

    private MockRestServiceServer photon;
    private AddressFinder finder;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://photon.test").defaultHeader("User-Agent", "Electriplan test");
        photon = MockRestServiceServer.bindTo(builder).build();
        finder = new AddressFinder(builder.build(), true);
    }

    private static String feature(String properties, double lon, double lat) {
        return """
                {"type": "Feature", "geometry": {"type": "Point", "coordinates": [%s, %s]}, "properties": {%s}}"""
                .formatted(lon, lat, properties);
    }

    private void answer(String... features) {
        photon.expect(requestTo(Matchers.startsWith("https://photon.test/api/")))
                .andRespond(withSuccess("{\"type\": \"FeatureCollection\", \"features\": [" + String.join(",", features) + "]}",
                        MediaType.APPLICATION_JSON));
    }

    @Test
    void asksPhotonForAustralianHousesAndStreets() {
        photon.expect(requestTo(Matchers.startsWith("https://photon.test/api/")))
                .andExpect(queryParam("q", "12%20Glenlyon%20Rd"))
                .andExpect(queryParam("bbox", AddressFinder.AUSTRALIA))
                .andExpect(queryParam("layer", "house", "street"))
                .andExpect(queryParam("lang", "en"))
                .andExpect(header("User-Agent", "Electriplan test"))
                .andRespond(withSuccess("{\"features\": []}", MediaType.APPLICATION_JSON));

        assertThat(finder.find("  12 Glenlyon Rd ")).isEmpty();
        photon.verify();
    }

    @Test
    void splitsAnAddressIntoTheFormsFields() {
        answer(feature("""
                "type": "house", "housenumber": "12", "street": "Glenlyon Road", "district": "Brunswick", "city": "Melbourne",
                "state": "Victoria", "postcode": "3056", "countrycode": "AU\"""", 144.96, -37.77));

        List<AddressSuggestion> found = finder.find("12 Glenlyon");

        assertThat(found).containsExactly(new AddressSuggestion("12 Glenlyon Road, Brunswick VIC 3056", "12 Glenlyon Road",
                "Brunswick", AustralianState.VIC, "3056", -37.77, 144.96));
    }

    @Test
    void usesTheTownWhenThereIsNoSuburbAndTheStreetWhenThereIsNoNumber() {
        answer(feature("""
                "type": "street", "name": "Main Street", "city": "Ballarat", "state": "Victoria", "postcode": "3350", "countrycode": "AU\"""", 143.8, -37.5),
                feature("""
                "type": "house", "street": "Smith Lane", "locality": "Kew", "state": "New South Wales", "countrycode": "AU\"""", 151.0, -33.0));

        List<AddressSuggestion> found = finder.find("main street");

        assertThat(found).extracting(AddressSuggestion::label).containsExactly("Main Street, Ballarat VIC 3350", "Smith Lane, Kew NSW");
        assertThat(found.get(1).postcode()).isNull();
        assertThat(found.get(1).state()).isEqualTo(AustralianState.NSW);
    }

    @Test
    void leavesOutWhatIsNotAnAustralianAddress() {
        answer(feature("\"type\": \"house\", \"street\": \"Queen Street\", \"city\": \"Auckland\", \"countrycode\": \"NZ\"", 174.7, -36.8),
                feature("\"type\": \"house\", \"street\": \"Christmas Road\", \"state\": \"Christmas Island\", \"countrycode\": \"AU\"", 105.6, -10.4),
                feature("\"type\": \"house\", \"state\": \"Victoria\", \"countrycode\": \"AU\"", 144.0, -37.0));

        assertThat(finder.find("street")).isEmpty();
    }

    @Test
    void offersEachAddressOnce() {
        String same = "\"type\": \"house\", \"housenumber\": \"1\", \"street\": \"Spring Street\", \"district\": \"Melbourne\", "
                + "\"state\": \"Victoria\", \"postcode\": \"3000\", \"countrycode\": \"AU\"";
        answer(feature(same, 144.97, -37.81), feature(same + ", \"name\": \"Entrance\"", 144.98, -37.81));

        assertThat(finder.find("1 spring st")).hasSize(1);
    }

    @Test
    void waitsForThreeCharactersBeforeAsking() {
        assertThat(finder.find("12")).isEmpty();
        assertThat(finder.find("   ")).isEmpty();
        photon.verify();
    }

    @Test
    void aProviderFailureIsUnavailableNotAnError() {
        photon.expect(requestTo(Matchers.startsWith("https://photon.test/api/"))).andRespond(withServerError());
        assertThatThrownBy(() -> finder.find("12 Glenlyon"))
                .isInstanceOf(AddressSearchUnavailableException.class)
                .hasMessage("Address search is not available right now. Type the address instead.");
    }

    @Test
    void turnedOffItSaysSoWithoutAsking() {
        AddressFinder off = new AddressFinder(RestClient.builder().build(), false);
        assertThatThrownBy(() -> off.find("12 Glenlyon"))
                .isInstanceOf(AddressSearchUnavailableException.class)
                .hasMessageContaining("turned off");
        assertThat(off.find("12")).isEmpty();
    }
}
