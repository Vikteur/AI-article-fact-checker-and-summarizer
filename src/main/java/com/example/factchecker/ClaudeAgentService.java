package com.example.factchecker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class ClaudeAgentService {

    private static final String FACT_CHECKER_SYSTEM = """
            You are an expert fact-checker. Your job is to carefully read an article and identify any claims that are factually incorrect, misleading, or dubious.

            For each problematic claim:
            - Quote the specific claim from the article
            - Explain concisely why it is incorrect or questionable
            - Provide the correct information where possible

            If you find no factual issues, respond with: "No significant factual issues found in this article."

            Focus only on verifiable facts — not opinions or writing style. Be precise and concise.
            """;

    private static final String SUMMARIZER_SYSTEM = """
            You are a concise, accurate summarizer. Your job is to distill an article into its essential points.

            Write a summary that:
            - Is 3–5 sentences long
            - Covers the main topic and key points
            - Uses clear, plain language
            - Preserves important facts and context

            Do not add information that is not in the article.
            """;

    private final WebClient webClient;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    public ClaudeAgentService(@Value("${claude.api.url}") String apiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Runs both agents in parallel — they both read the original article
     * independently, so there's no reason to sequence them.
     */
    public AnalyzeResponse analyze(String articleText) {
        CompletableFuture<String> factCheckFuture = CompletableFuture.supplyAsync(() ->
                callAgent(FACT_CHECKER_SYSTEM,
                        "Please fact-check the following article and identify any incorrect or dubious claims:\n\n" + articleText));

        CompletableFuture<String> summaryFuture = CompletableFuture.supplyAsync(() ->
                callAgent(SUMMARIZER_SYSTEM,
                        "Please summarize the following article:\n\n" + articleText));

        String incorrectFacts = factCheckFuture.join();
        String summary = summaryFuture.join();

        return new AnalyzeResponse(incorrectFacts, summary);
    }

    /**
     * Makes a single call to the Claude API with a given system prompt and user message.
     * This is the core building block — swap the system prompt and you have a different agent.
     */
    @SuppressWarnings("unchecked")
    private String callAgent(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "system", systemPrompt,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                )
        );

        Map<String, Object> response = webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }
}
