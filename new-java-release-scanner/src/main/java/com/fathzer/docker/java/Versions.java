package com.fathzer.docker.java;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.regex.Pattern;

import com.fathzer.docker.java.DockerHubScanner.Tag;

public class Versions {

    public static void main(String[] args) throws IOException, InterruptedException {
        String user = System.getenv("DOCKER_USER");
        if (user == null || user.isBlank()) {
            throw new IllegalStateException("DOCKER_USER environment variable is not set");
        }
        String token = System.getenv("DOCKER_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("DOCKER_TOKEN environment variable is not set");
        }
        TemurinVersions temurinVersions = new TemurinVersions(user, token);

        String appVersion = "1.1.0";
        Pattern appTagPattern = Pattern.compile("^" + Pattern.quote(appVersion) + "-jre-\\d+\\.\\d+\\.\\d+$");
        System.out.println(temurinVersions.buildMatrix("fathzer/db-backup", 11, 25, null/*Instant.now().minus(Duration.ofDays(30))*/,
            appTagPattern.asPredicate(),
            tag -> {
                String prefix = appVersion + "-jre-";
                if (!tag.startsWith(prefix)) {
                    return null;
                }
                String jreVersion = tag.substring(prefix.length());
                String[] parts = jreVersion.split("\\.");
                return new TemurinVersions.Version(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
                );
            },
            v -> appVersion + "-jre-" + v.javaVersion()
        ));
    }
}
