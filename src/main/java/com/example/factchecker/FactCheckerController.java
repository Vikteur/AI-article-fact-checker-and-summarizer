package com.example.factchecker;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class FactCheckerController {

    private final ClaudeAgentService claudeAgentService;

    public FactCheckerController(ClaudeAgentService claudeAgentService) {
        this.claudeAgentService = claudeAgentService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalyzeResponse> analyze(@RequestBody AnalyzeRequest request) {
        if (request.getArticle() == null || request.getArticle().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new AnalyzeResponse("Please provide an article to analyze.", ""));
        }

        AnalyzeResponse result = claudeAgentService.analyze(request.getArticle());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Claude Fact Checker is running!");
    }
}
