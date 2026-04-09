package com.overzealouspelican.awsui.service;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Cluster;
import software.amazon.awssdk.services.ecs.model.Service;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * ECS service wrapper for loading clusters/services and triggering deployments.
 */
public class EcsService {

    private static final DateTimeFormatter TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public record ClusterRow(
        String name,
        String arn,
        String status,
        int activeServices,
        int runningTasks,
        int pendingTasks,
        int containerInstances
    ) {}

    public record ServiceRow(
        String name,
        String arn,
        String status,
        String launchType,
        String schedulingStrategy,
        int desiredCount,
        int runningCount,
        int pendingCount,
        String taskDefinition,
        String deploymentStatus,
        String lastDeploymentAt,
        String createdAt
    ) {}

    public List<ClusterRow> listClusters(String profileName, String query) {
        String region = resolveRegion(profileName);
        try (EcsClient client = buildClient(profileName, region)) {
            List<String> clusterArns = new ArrayList<>();
            String nextToken = null;
            do {
                var request = software.amazon.awssdk.services.ecs.model.ListClustersRequest.builder()
                    .maxResults(100);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }
                var response = client.listClusters(request.build());
                clusterArns.addAll(response.clusterArns());
                nextToken = response.nextToken();
            } while (nextToken != null);

            List<ClusterRow> rows = new ArrayList<>();
            for (int i = 0; i < clusterArns.size(); i += 100) {
                List<String> batch = clusterArns.subList(i, Math.min(i + 100, clusterArns.size()));
                var describe = client.describeClusters(r -> r.clusters(batch));
                for (Cluster cluster : describe.clusters()) {
                    String name = cluster.clusterName();
                    if (!matchesQuery(name, query)) {
                        continue;
                    }
                    rows.add(new ClusterRow(
                        name,
                        cluster.clusterArn(),
                        safe(cluster.status()),
                        cluster.activeServicesCount() == null ? 0 : cluster.activeServicesCount(),
                        cluster.runningTasksCount() == null ? 0 : cluster.runningTasksCount(),
                        cluster.pendingTasksCount() == null ? 0 : cluster.pendingTasksCount(),
                        cluster.registeredContainerInstancesCount() == null ? 0 : cluster.registeredContainerInstancesCount()
                    ));
                }
            }
            rows.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return rows;
        }
    }

    public List<ServiceRow> listServices(String profileName, String clusterArn, String query) {
        String region = resolveRegion(profileName);
        try (EcsClient client = buildClient(profileName, region)) {
            List<String> serviceArns = new ArrayList<>();
            String nextToken = null;
            do {
                var request = software.amazon.awssdk.services.ecs.model.ListServicesRequest.builder()
                    .cluster(clusterArn)
                    .maxResults(100);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }
                var response = client.listServices(request.build());
                serviceArns.addAll(response.serviceArns());
                nextToken = response.nextToken();
            } while (nextToken != null);

            List<ServiceRow> rows = new ArrayList<>();
            for (int i = 0; i < serviceArns.size(); i += 10) {
                List<String> batch = serviceArns.subList(i, Math.min(i + 10, serviceArns.size()));
                var describe = client.describeServices(r -> r.cluster(clusterArn).services(batch));
                for (Service service : describe.services()) {
                    if (!matchesQuery(service.serviceName(), query)) {
                        continue;
                    }
                    String deploymentStatus = service.deployments().isEmpty()
                        ? "-"
                        : safe(service.deployments().get(0).rolloutStateAsString());
                    String lastDeploymentAt = service.deployments().isEmpty() || service.deployments().get(0).updatedAt() == null
                        ? "-"
                        : TIME_FORMATTER.format(service.deployments().get(0).updatedAt());
                    String createdAt = service.createdAt() == null ? "-" : TIME_FORMATTER.format(service.createdAt());
                    rows.add(new ServiceRow(
                        service.serviceName(),
                        service.serviceArn(),
                        safe(service.status()),
                        safe(service.launchTypeAsString()),
                        safe(service.schedulingStrategyAsString()),
                        service.desiredCount() == null ? 0 : service.desiredCount(),
                        service.runningCount() == null ? 0 : service.runningCount(),
                        service.pendingCount() == null ? 0 : service.pendingCount(),
                        safe(service.taskDefinition()),
                        deploymentStatus,
                        lastDeploymentAt,
                        createdAt
                    ));
                }
            }
            rows.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            return rows;
        }
    }

    public void forceNewDeployment(String profileName, String clusterArn, String serviceName) {
        String region = resolveRegion(profileName);
        try (EcsClient client = buildClient(profileName, region)) {
            client.updateService(r -> r
                .cluster(clusterArn)
                .service(serviceName)
                .forceNewDeployment(true));
        }
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private boolean matchesQuery(String value, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private EcsClient buildClient(String profileName, String region) {
        return EcsClient.builder()
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
