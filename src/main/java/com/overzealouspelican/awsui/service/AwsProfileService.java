package com.overzealouspelican.awsui.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads available AWS profile names from ~/.aws/credentials.
 */
public class AwsProfileService {

    private static final Pattern PROFILE_HEADER_PATTERN = Pattern.compile("^\\s*\\[\\s*([^\\]]+)\\s*]\\s*$");

    public List<String> getProfiles() {
        Path credentialsPath = Paths.get(System.getProperty("user.home"), ".aws", "credentials");
        if (!Files.exists(credentialsPath)) {
            return List.of();
        }

        Set<String> uniqueProfiles = new LinkedHashSet<>();
        try {
            List<String> lines = Files.readAllLines(credentialsPath, StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher matcher = PROFILE_HEADER_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String profile = matcher.group(1).trim();
                if (profile.startsWith("profile ")) {
                    profile = profile.substring("profile ".length()).trim();
                }

                if (!profile.isEmpty()) {
                    uniqueProfiles.add(profile);
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }

        return new ArrayList<>(uniqueProfiles);
    }
}
