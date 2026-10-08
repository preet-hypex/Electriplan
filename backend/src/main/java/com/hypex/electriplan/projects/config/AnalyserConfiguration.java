package com.hypex.electriplan.projects.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client for the floor-plan analyser (the Python service). It only
 * analyses: the API stores the image and the result. An analysis can take a
 * minute, so the wait is long.
 */
@Configuration(proxyBeanMethods = false)
public class AnalyserConfiguration {

    public static final String CLIENT = "analyserClient";

    @Bean
    @Qualifier(CLIENT)
    RestClient analyserClient(RestClient.Builder builder,
                              @Value("${electriplan.analyser.url:http://localhost:8082}") String url,
                              @Value("${electriplan.analyser.timeout:180s}") Duration timeout) {
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(5))
                .withReadTimeout(timeout);
        return builder.clone()
                .baseUrl(url)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }
}
