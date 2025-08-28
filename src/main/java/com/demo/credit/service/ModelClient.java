package com.demo.credit.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import java.util.Map;

@Service
public class ModelClient {
    private final WebClient client;

    public ModelClient(@Value("${ai.base-url}") String baseUrl) {
        this.client = WebClient.builder().baseUrl(baseUrl).build();
    }

    public Map<String, Object> score(Map<String, Object> payload) {
        return client.post()
                .uri("/score")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .onErrorResume(e -> Mono.just(Map.of("error", e.getMessage())))
                .block();
    }
}
