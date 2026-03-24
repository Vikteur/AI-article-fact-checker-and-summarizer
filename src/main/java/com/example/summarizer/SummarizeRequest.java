package com.example.summarizer;

/**
 * The request body the user sends to our API.
 * Example JSON: { "text": "Long article or email text here..." }
 */
public class SummarizeRequest {

    private String text;

    public SummarizeRequest() {}

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
