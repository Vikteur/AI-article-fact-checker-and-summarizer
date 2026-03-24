package com.example.summarizer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Multi-Agent Pipeline: News Briefing Generator
 *
 * Three specialized agents work in sequence, each passing its output
 * to the next as input:
 *
 *   [Raw Article]
 *       ↓
 *   Agent 1 — Summarizer   : Condenses the article into key facts
 *       ↓
 *   Agent 2 — Analyst      : Adds sentiment + business impact analysis
 *       ↓
 *   Agent 3 — Editor       : Formats everything into a polished briefing
 *       ↓
 *   [Final Briefing]
 *
 * Each agent is just a Claude API call with a different system prompt
 * (its "personality" and "job description").
 */
@Service
public class AgentPipelineService {

    private final WebClient webClient;

    @Value("${claude.api.key}")
    private String apiKey;

    @Value("${claude.model}")
    private String model;

    public AgentPipelineService(@Value("${claude.api.url}") String apiUrl) {
        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * Runs the full 3-agent pipeline on a piece of text.
     *
     * @param articleText The raw article or document to process
     * @return A BriefingResult containing each agent's output + the final briefing
     */
    public BriefingResult runPipeline(String articleText) {

        // ── Agent 1: Summarizer ───────────────────────────────────────────────
        // Its job: strip the article down to the raw facts, nothing else.
        String summarizerSystemPrompt = """
                You are a precise fact extractor. Your only job is to read a piece
                of text and extract the 4-6 most important factual statements.
                Output ONLY the facts as a numbered list. No opinions, no filler.
                """;

        String facts = callAgent(summarizerSystemPrompt,
                "Extract the key facts from this article:\n\n" + articleText);

        // ── Agent 2: Analyst ──────────────────────────────────────────────────
        // Its job: take the facts and add analytical depth — sentiment, impact.
        // It receives Agent 1's output, not the original article.
        String analystSystemPrompt = """
                You are a sharp business analyst. Given a list of facts, you will:
                1. Identify the overall sentiment (Positive / Neutral / Negative) and explain why in one sentence.
                2. List 2-3 key business or practical implications.
                Keep it concise and direct.
                """;

        String analysis = callAgent(analystSystemPrompt,
                "Analyse these extracted facts:\n\n" + facts);

        // ── Agent 3: Editor ───────────────────────────────────────────────────
        // Its job: receive both previous outputs and produce a clean, final briefing.
        String editorSystemPrompt = """
                You are a professional editor creating executive briefings.
                You will receive a list of facts and an analysis.
                Combine them into a clean, structured briefing with these exact sections:
                  📋 Summary (2 sentences max)
                  📊 Key Facts (bullet list)
                  🔍 Analysis (sentiment + implications)
                  ⚡ Bottom Line (one sentence takeaway)
                """;

        String editorInput = """
                FACTS:
                %s

                ANALYSIS:
                %s
                """.formatted(facts, analysis);

        String briefing = callAgent(editorSystemPrompt,
                "Produce the final briefing from these inputs:\n\n" + editorInput);

        // Return all intermediate outputs so the blog post can show what each agent did
        return new BriefingResult(facts, analysis, briefing);
    }

    /**
     * Helper: makes a single Claude API call with a system prompt + user message.
     * This is the building block every "agent" uses.
     *
     * @param systemPrompt The agent's personality / job description
     * @param userMessage  The input this agent needs to process
     * @return The agent's text response
     */
    private String callAgent(String systemPrompt, String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 1024,
                "messages", List.of(
                        Map.of("role", "user", "content", userMessage)
                ),
                "system", systemPrompt   // ← This is what makes each agent unique
        );

        Map<String, Object> response = webClient.post()
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        List<Map<String, Object>> content =
                (List<Map<String, Object>>) response.get("content");
        return (String) content.get(0).get("text");
    }

    /**
     * Holds the output of each agent in the pipeline.
     */
    public record BriefingResult(String extractedFacts, String analysis, String finalBriefing) {}
}
