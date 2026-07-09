package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CodePipelineRefreshPolicyTest {

    @Test
    void doesNotAutoRefreshWhenThereAreNoPhases() {
        assertFalse(CodePipelineRefreshPolicy.shouldAutoRefresh(List.of()));
    }

    @Test
    void autoRefreshesWhenAnyPhaseIsMissingOrNotSucceeded() {
        List<CodePipelineService.PhaseState> phases = List.of(
            new CodePipelineService.PhaseState("Source", "Succeeded", List.of()),
            new CodePipelineService.PhaseState("Build", "InProgress", List.of())
        );

        assertTrue(CodePipelineRefreshPolicy.shouldAutoRefresh(phases));
    }

    @Test
    void stopsAutoRefreshWhenAllPhasesSucceeded() {
        List<CodePipelineService.PhaseState> phases = List.of(
            new CodePipelineService.PhaseState("Source", "Succeeded", List.of()),
            new CodePipelineService.PhaseState("Build", "Succeeded", List.of())
        );

        assertFalse(CodePipelineRefreshPolicy.shouldAutoRefresh(phases));
    }
}