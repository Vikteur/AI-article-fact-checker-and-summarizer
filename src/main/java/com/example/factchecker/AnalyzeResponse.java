package com.example.factchecker;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AnalyzeResponse {
    private String incorrectFacts;
    private String summary;
}
