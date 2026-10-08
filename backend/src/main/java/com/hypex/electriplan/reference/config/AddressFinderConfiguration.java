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
 * (https://github.com/komoot/photon, OpenStreetMap data). Short timeouts,
 * because people are typing and waiting.
 */
@Configuration(proxyBeanMethods = false)
public class AddressFinderConfiguration {

    public static final String CLIENT = "addressFinderClient";

    @Bean
    @Qualifier(CLIENT)
    RestClient addressFinderClient(RestClient.Builder builder,
                                   @Value("${electriplan.addresses.url:https://photon.komoot.io}") String url) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(4));
        return builder.clone()
                .baseUrl(url)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                // Public Photon servers ask callers to say who they are.
                .defaultHeader(HttpHeaders.USER_AGENT, "Electriplan address finder (https://github.com/preet-hypex/Electriplan)")
                .build();
    }
}
