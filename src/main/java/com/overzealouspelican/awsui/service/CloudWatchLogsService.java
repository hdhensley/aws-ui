package com.overzealouspelican.awsui.service;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileProperty;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilterLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.FilteredLogEvent;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Service for listing CloudWatch log groups/streams and fetching log events.
 */
public class CloudWatchLogsService {

    private static final DateTimeFormatter STREAM_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public record LogStreamOption(String name, String displayName, Long lastEventTimestamp) {
        @Override
        public String toString() {
            return displayName;
        }
    }

    public List<String> listLogGroups(String profileName, String query) {
        String region = resolveRegion(profileName);
        try (CloudWatchLogsClient client = buildClient(profileName, region)) {
            List<String> groups = new ArrayList<>();
            String nextToken = null;
            do {
                var request = software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest
                    .builder()
                    .limit(50);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }

                var response = client.describeLogGroups(request.build());
                response.logGroups().forEach(group -> groups.add(group.logGroupName()));
                nextToken = response.nextToken();
            } while (nextToken != null && groups.size() < 1000);

            return groups.stream()
                .filter(name -> matchesQuery(name, query))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
        }
    }

    public List<LogStreamOption> listLogStreams(String profileName, String logGroupName, String query) {
        if (logGroupName == null || logGroupName.isBlank()) {
            return List.of();
        }

        String region = resolveRegion(profileName);
        try (CloudWatchLogsClient client = buildClient(profileName, region)) {
            List<StreamInfo> streams = new ArrayList<>();
            String nextToken = null;
            do {
                var request = software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest
                    .builder()
                    .logGroupName(logGroupName)
                    .orderBy("LastEventTime")
                    .descending(true)
                    .limit(50);
                if (nextToken != null) {
                    request.nextToken(nextToken);
                }

                var response = client.describeLogStreams(request.build());
                response.logStreams().forEach(s -> streams.add(new StreamInfo(s.logStreamName(), s.lastEventTimestamp())));
                nextToken = response.nextToken();
            } while (nextToken != null && streams.size() < 2000);

            return streams.stream()
                .filter(s -> matchesQuery(s.name(), query))
                .sorted(Comparator.comparingLong((StreamInfo s) -> s.lastEventTimestamp() == null ? 0L : s.lastEventTimestamp()).reversed())
                .map(s -> new LogStreamOption(s.name(), formatStreamDisplayName(s), s.lastEventTimestamp()))
                .toList();
        }
    }

    public String getLogEvents(String profileName, String logGroupName, String logStreamName, Instant startTime, Instant endTime) {
        if (logGroupName == null || logGroupName.isBlank()) {
            return "Select a log group.";
        }

        String region = resolveRegion(profileName);
        try (CloudWatchLogsClient client = buildClient(profileName, region)) {
            StringBuilder output = new StringBuilder();
            String nextToken = null;
            int scanned = 0;

            do {
                FilterLogEventsRequest.Builder request = FilterLogEventsRequest.builder()
                    .logGroupName(logGroupName)
                    .startTime(startTime.toEpochMilli())
                    .endTime(endTime.toEpochMilli())
                    .limit(1000);

                if (logStreamName != null && !logStreamName.isBlank()) {
                    request.logStreamNames(logStreamName);
                }

                if (nextToken != null) {
                    request.nextToken(nextToken);
                }

                var response = client.filterLogEvents(request.build());
                for (FilteredLogEvent event : response.events()) {
                    output.append(Instant.ofEpochMilli(event.timestamp()))
                        .append("  ")
                        .append(event.message() == null ? "" : event.message().stripTrailing())
                        .append(System.lineSeparator());
                    scanned++;
                    if (scanned >= 2000) {
                        output.append(System.lineSeparator())
                            .append("... output truncated at 2000 events ...")
                            .append(System.lineSeparator());
                        return output.toString();
                    }
                }

                String newToken = response.nextToken();
                if (newToken == null || newToken.equals(nextToken)) {
                    break;
                }
                nextToken = newToken;
            } while (true);

            return output.isEmpty() ? "No log events found for the selected timeframe." : output.toString();
        }
    }

    private boolean matchesQuery(String value, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private String formatStreamDisplayName(StreamInfo streamInfo) {
        if (streamInfo.lastEventTimestamp() == null) {
            return streamInfo.name() + "  |  no events";
        }

        return streamInfo.name() + "  |  last event "
            + STREAM_TIME_FORMATTER.format(Instant.ofEpochMilli(streamInfo.lastEventTimestamp()));
    }

    private CloudWatchLogsClient buildClient(String profileName, String region) {
        return CloudWatchLogsClient.builder()
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

    private record StreamInfo(String name, Long lastEventTimestamp) {
    }
}
