package com.overzealouspelican.awsui.service;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.codepipeline.CodePipelineClient;
import software.amazon.awssdk.services.codepipeline.model.PipelineExecutionStatus;
import software.amazon.awssdk.services.codepipeline.model.PipelineExecutionSummary;
import software.amazon.awssdk.services.codepipeline.model.StageState;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Fetches and manages AWS CodePipeline resources using SDK v2 profile-based credentials.
 * Role-based profiles (role_arn in ~/.aws/config) are handled automatically by the SDK.
 */
public class CodePipelineService {

    private static final DateTimeFormatter DEPLOYED_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public record PipelineRow(
        String name,
        String status,
        String region,
        String lastDeployedAt,
        String inProgressExecutionId,        // null when pipeline is not running
        List<String> recentStatuses          // newest first, up to 5
    ) {}

    /**
     * Lists all pipelines in the account/region for the given profile, including their latest
     * execution status. One extra API call is made per pipeline (listPipelineExecutions).
     */
    public List<PipelineRow> listPipelines(String profileName) {
        String region = resolveRegion(profileName);
        try (CodePipelineClient client = buildClient(profileName, region)) {
            List<PipelineRow> rows = new ArrayList<>();
            for (var summary : client.listPipelines().pipelines()) {
                String status = "No executions";
                String lastDeployedAt = "-";
                String executionId = null;
                List<String> recentStatuses = List.of();
                try {
                    List<PipelineExecutionSummary> executions = client
                        .listPipelineExecutions(r -> r.pipelineName(summary.name()).maxResults(5))
                        .pipelineExecutionSummaries();
                    if (!executions.isEmpty()) {
                        PipelineExecutionSummary latest = executions.get(0);
                        status = latest.statusAsString();
                        Instant latestTime = latest.lastUpdateTime() != null
                            ? latest.lastUpdateTime()
                            : latest.startTime();
                        if (latestTime != null) {
                            lastDeployedAt = DEPLOYED_TIME_FORMATTER.format(latestTime);
                        }
                        if (latest.status() == PipelineExecutionStatus.IN_PROGRESS
                                || latest.status() == PipelineExecutionStatus.STOPPING) {
                            executionId = latest.pipelineExecutionId();
                        }
                        recentStatuses = executions.stream()
                            .map(PipelineExecutionSummary::statusAsString)
                            .collect(Collectors.toList());
                    }
                } catch (Exception ignored) {
                    status = "Unknown";
                }
                rows.add(new PipelineRow(summary.name(), status, region, lastDeployedAt, executionId, recentStatuses));
            }
            return rows;
        }
    }

    public void startPipeline(String profileName, String pipelineName) {
        String region = resolveRegion(profileName);
        try (CodePipelineClient client = buildClient(profileName, region)) {
            client.startPipelineExecution(r -> r.name(pipelineName));
        }
    }

    public void stopPipeline(String profileName, String pipelineName, String executionId) {
        String region = resolveRegion(profileName);
        try (CodePipelineClient client = buildClient(profileName, region)) {
            client.stopPipelineExecution(r -> r
                .pipelineName(pipelineName)
                .pipelineExecutionId(executionId)
                .abandon(false));
        }
    }

    public List<String> getPipelinePhases(String profileName, String pipelineName) {
        String region = resolveRegion(profileName);
        try (CodePipelineClient client = buildClient(profileName, region)) {
            var state = client.getPipelineState(r -> r.name(pipelineName));
            List<String> phases = new ArrayList<>();
            for (StageState stage : state.stageStates()) {
                String status = stage.latestExecution() == null
                    ? "-"
                    : stage.latestExecution().statusAsString();
                phases.add(stage.stageName() + ": " + status);
            }
            return phases;
        }
    }

    private CodePipelineClient buildClient(String profileName, String region) {
        return CodePipelineClient.builder()
            .credentialsProvider(ProfileCredentialsProvider.create(profileName))
            .region(Region.of(region))
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build();
    }

    private String resolveRegion(String profileName) {
        try {
            return ProfileFile.defaultProfileFile()
                .profile(profileName)
                .flatMap(p -> p.property(ProfileProperty.REGION))
                .orElse(Region.US_EAST_1.id());
        } catch (Exception ignored) {
            return Region.US_EAST_1.id();
        }
    }
}
