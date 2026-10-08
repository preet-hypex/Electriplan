package com.hypex.electriplan.reference.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client for the address finder's provider: a Photon server
 * (https://github.com/komoot/photon, OpenStreetMap data). The public server
 * can take several seconds; our own answers in milliseconds. The read
 * timeout is electriplan.addresses.timeout (10s by default).
 */
@Configuration(proxyBeanMethods = false)
public class AddressFinderConfiguration {

    public static final String CLIENT = "addressFinderClient";

    @Bean
    @Qualifier(CLIENT)
    RestClient addressFinderClient(RestClient.Builder builder,
                                   @Value("${electriplan.addresses.url:https://photon.komoot.io}") String url,
                                   @Value("${electriplan.addresses.timeout:10s}") Duration timeout) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(3))
                .withReadTimeout(timeout);
        return builder.clone()
                .baseUrl(url)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // Public Photon servers ask callers to say who they are.
                .defaultHeader(HttpHeaders.USER_AGENT, "Electriplan address finder (https://github.com/preet-hypex/Electriplan)")
                .build();
    }
}
