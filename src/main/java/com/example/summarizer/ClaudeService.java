package com.example.summarizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * This service handles all communication with the Claude API.
 *
 * The flow is simple:
 *   1. We build a JSON request body with our prompt
 *   2. We POST it to https://api.anthropic.com/v1/messages
 *   3. We extract the text from the response
 */
@Service
public class ClaudeService {

    private final WebClient webClient;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    public ClaudeService(@Value("${claude.api.url}") String apiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Sends the given text to Claude and asks it to summarize it.
     *
     * @param text The full text to summarize (an email, article, meeting notes, etc.)
     * @return A concise bullet-point summary
     */
    public String summarize(String text) {

        // 1. Build the prompt
        String prompt = """
                Please summarize the following text in 3-5 bullet points.
                Be concise and focus on the most important information.

                Text to summarize:
                ---
                %s
                ---
                """.formatted(text);

        // 2. Build the request body that the Claude API expects
        //    See: https://docs.anthropic.com/en/api/messages
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", prompt
                        )
                )
        );

        // 3. Make the HTTP POST request and parse the response
        Map<String, Object> response = webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block(); // block() makes this synchronous (simpler for a demo)

        // 4. Extract the text from the response
        //    The response looks like: { "content": [ { "type": "text", "text": "..." } ] }
        if (response != null && response.containsKey("content")) {
            List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
            if (!content.isEmpty()) {
                return (String) content.get(0).get("text");
            }
        }

        return "Could not generate a summary. Please try again.";
    }
}
