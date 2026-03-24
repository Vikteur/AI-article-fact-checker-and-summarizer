package com.example.summarizer;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing two endpoints:
 *
 *   POST /api/summarize  — Single-agent: summarize any text
 *   POST /api/briefing   — Multi-agent pipeline: full news briefing from raw article
 *   GET  /api/health     — Health check
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SummarizerController {

    private final ClaudeService claudeService;
    private final AgentPipelineService agentPipelineService;

    public SummarizerController(ClaudeService claudeService,
                                AgentPipelineService agentPipelineService) {
        this.claudeService = claudeService;
        this.agentPipelineService = agentPipelineService;
    }

    // ── Single-agent endpoint ──────────────────────────────────────────────

    @PostMapping("/summarize")
    public ResponseEntity<SummarizeResponse> summarize(@RequestBody SummarizeRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new SummarizeResponse("Please provide some text to summarize."));
        }
        String summary = claudeService.summarize(request.getText());
        return ResponseEntity.ok(new SummarizeResponse(summary));
    }

    // ── Multi-agent pipeline endpoint ──────────────────────────────────────

    /**
     * Runs the 3-agent briefing pipeline:
     *   1. Summarizer agent  → extracts key facts
     *   2. Analyst agent     → adds sentiment + implications
     *   3. Editor agent      → formats into a polished briefing
     *
     * Returns all intermediate outputs so you can see each agent's contribution.
     */
    @PostMapping("/briefing")
    public ResponseEntity<?> briefing(@RequestBody BriefingRequest request) {
        if (request.getArticle() == null || request.getArticle().isBlank()) {
            return ResponseEntity.badRequest().body("Please provide an article.");
        }

        AgentPipelineService.BriefingResult result =
                agentPipelineService.runPipeline(request.getArticle());

        // Return all stages so the caller can see what each agent produced
        return ResponseEntity.ok(new java.util.LinkedHashMap<>() {{
            put("stage_1_facts",    result.extractedFacts());
            put("stage_2_analysis", result.analysis());
            put("stage_3_briefing", result.finalBriefing());
        }});
    }

    // ── Health check ───────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Claude Summarizer is running!");
    }
}
