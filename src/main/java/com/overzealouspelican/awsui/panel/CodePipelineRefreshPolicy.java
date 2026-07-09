package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;

import java.util.List;

final class CodePipelineRefreshPolicy {
    private CodePipelineRefreshPolicy() {
    }

    static boolean shouldAutoRefresh(List<CodePipelineService.PhaseState> phases) {
        if (phases == null || phases.isEmpty()) {
            return false;
        }

        for (CodePipelineService.PhaseState phase : phases) {
            if (phase == null) {
                return true;
            }

            String status = phase.status();
            if (status == null || status.isBlank() || !status.contains("Succeeded")) {
                return true;
            }
        }

        return false;
    }
}