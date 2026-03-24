package com.example.summarizer;

/**
 * The response body we return to the user.
 * Example JSON: { "summary": "This article is about..." }
 */
public class SummarizeResponse {

    private String summary;

    public SummarizeResponse(String summary) {
        this.summary = summary;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }
}
