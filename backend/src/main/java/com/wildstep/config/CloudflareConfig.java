package com.wildstep.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class CloudflareConfig {

    @Value("${cloudflare.timeout-seconds:30}")
    private int timeoutSeconds;

    @Bean
    public RestClient cloudflareRestClient(RestClient.Builder builder) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));

        return builder
                .baseUrl("https://api.cloudflare.com/client/v4")
                .requestFactory(requestFactory)
                .build();
    }
}
