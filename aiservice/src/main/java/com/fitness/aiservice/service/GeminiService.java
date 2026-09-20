package com.fitness.aiservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Service
@Slf4j
public class GeminiService {

    private final WebClient webClient;

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    public GeminiService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    public String getRecommendation(String details) {

        log.info("Inside GeminiService");
        log.info("Sending request to Gemini API...");
        //log.info("Gemini URL = [{}]", geminiApiUrl);

        // Request body for Gemini Interactions API
        Map<String, Object> requestBody = Map.of(
                "model", "gemini-3.6-flash",
                "input", details
        );

        log.info("Sending request to Gemini...");

        try {

            String response = webClient.post()
                    .uri(geminiApiUrl.trim())
                    .header("x-goog-api-key", geminiApiKey.trim())
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            log.info("Gemini response received successfully");
            log.info("Gemini response: {}", response);

            return response;

        } catch (Exception e) {

            log.error("Error while calling Gemini API", e);

            throw new RuntimeException(
                    "Failed to get recommendation from Gemini API",
                    e
            );
        }
    }
}