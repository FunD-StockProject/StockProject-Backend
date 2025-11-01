package com.fund.stockProject.experiment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestResultResponse {
    private String testName;
    private boolean passed;
    private String message;
    private Object details;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
class ComprehensiveTestResult {
    private String summary;
    private int totalTests;
    private int passedTests;
    private int failedTests;
    private List<TestResultResponse> testResults;
}

