package com.fathzer.docker.java;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Entry point for the Docker image build decision tool.
 * <BR>Determines whether a new Docker image should be built and pushed for the latest
 * Eclipse Temurin JRE of a given Java major version, based on the tags already
 * published on Docker Hub.
 * <BR>Outputs a JSON object on stdout:
 * <pre>
 * {"shouldBuild":true,"baseImage":"eclipse-temurin:11.0.4_5-jre","javaVersion":"11.0.4","tags":["1.1.0-jre-11.0.4","1.1.0-jre-11","1.1.0","latest"]}
 * </pre>
 * or, when the latest Java version is already published:
 * <pre>
 * {"shouldBuild":false}
 * </pre>
 * <BR>Usage: {@code java -jar scanner.jar <appVersion> <javaMajor> [dockerImage]}
 * <BR>Environment variables: {@code DOCKER_USER} and {@code DOCKER_TOKEN} (Docker Hub credentials).
 */
public class Main {

  private static final String DEFAULT_IMAGE = "fathzer/db-backup";
  private static final String JRE_TAG_PREFIX = "-jre-";

  /** Main entry point.
   * @param args command line arguments:
   *   {@code args[0]} is the application version,
   *   {@code args[1]} is the target Java major version (e.g. 11),
   *   {@code args[2]} (optional) is the Docker Hub image (defaults to {@value #DEFAULT_IMAGE}).
   * @throws IOException if an HTTP error occurs.
   * @throws InterruptedException if the request is interrupted.
   */
  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: java -jar scanner.jar <appVersion> <javaMajor> [dockerImage]");
    }
    String appVersion = args[0];
    int javaMajor = Integer.parseInt(args[1]);
    String dockerImage = args.length > 2 ? args[2] : DEFAULT_IMAGE;

    String user = requireEnv("DOCKER_USER");
    String token = requireEnv("DOCKER_TOKEN");

    TemurinVersions temurinVersions = new TemurinVersions(user, token);

    // Tags already published on Docker Hub for this app version, e.g. 1.1.0-jre-11.0.4
    Pattern appTagPattern = Pattern.compile(
      "^" + Pattern.quote(appVersion) + JRE_TAG_PREFIX + "\\d+\\.\\d+\\.\\d+$"
    );

    ArrayNode matrix = temurinVersions.buildMatrix(
      dockerImage,
      javaMajor, javaMajor,
      null,
      appTagPattern.asPredicate(),
      tag -> {
        String prefix = appVersion + JRE_TAG_PREFIX;
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
      v -> appVersion + JRE_TAG_PREFIX + v.javaVersion()
    );

    ObjectMapper mapper = new ObjectMapper();
    ObjectNode result = mapper.createObjectNode();

    if (matrix.isEmpty()) {
      result.put("shouldBuild", false);
    } else {
      ObjectNode entry = (ObjectNode) matrix.get(0);
      String javaVersion = entry.get("javaVersion").asText();
      String temurinTag = entry.get("temurinTag").asText();

      // buildMatrix returns "library/eclipse-temurin:..." — strip the "library/" prefix
      String baseImage = "eclipse-temurin:" + temurinTag;

      result.put("shouldBuild", true);
      result.put("baseImage", baseImage);
      result.put("javaVersion", javaVersion);

      List<String> tags = new ArrayList<>();
      tags.add(appVersion + JRE_TAG_PREFIX + javaVersion);
      tags.add(appVersion + JRE_TAG_PREFIX + javaMajor);
      tags.add(appVersion);
      tags.add("latest");
      ArrayNode tagsNode = result.putArray("tags");
      tags.forEach(tagsNode::add);
    }

    System.out.println(mapper.writeValueAsString(result));
  }

  /** Reads a required environment variable.
   * @param name the environment variable name.
   * @return the value.
   * @throws IllegalStateException if the variable is not set or blank.
   */
  private static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " environment variable is not set");
    }
    return value;
  }
}
