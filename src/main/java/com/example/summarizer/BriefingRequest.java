package com.example.summarizer;

/**
 * Request body for the multi-agent briefing endpoint.
 * Example JSON: { "article": "Long news article text here..." }
 */
public class BriefingRequest {

    private String article;

    public BriefingRequest() {}

    public String getArticle() {
        return article;
    }

    public void setArticle(String article) {
        this.article = article;
    }
}
