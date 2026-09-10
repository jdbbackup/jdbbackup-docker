package com.fathzer.docker.java;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Scans Docker Hub for available Eclipse Temurin JRE tags and compares them
 * with the tags already published on a target image.
 * <BR>Uses a {@link DockerHubScanner} for the actual API calls.
 */
public class TemurinVersions {

  private static final String TEMURIN_REPOSITORY = "library/eclipse-temurin";

  /*
   * Examples:
   *
   *   25.0.4_7-jre
   *   25.0.5_1-jre
   *   26.0.0_2-jre
   *
   * We deliberately ignore:
   *
   *   25-jre
   *   25.0-jre
   *   25.0.4_7-jre-alpine
   *   25.0.4_7-jre-noble
   */
  private static final Pattern TEMURIN_TAG = Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)_(\\d+)-jre$");

  private static final ObjectMapper JSON = new ObjectMapper();

  /** A Java version (major.minor.patch).
   * <BR>Equality is based on major.minor.patch only, so two versions with different builds are considered equal. */
  public record Version(int major, int minor, int patch) implements Comparable<Version> {
    /** Returns the Java version string (major.minor.patch). */
    public String javaVersion() {
      return major + "." + minor + "." + patch;
    }

    @Override
    public int compareTo(Version o) {
      int c = Integer.compare(major, o.major);
      if (c == 0) c = Integer.compare(minor, o.minor);
      if (c == 0) c = Integer.compare(patch, o.patch);
      return c;
    }
  }

  /** A Temurin JRE version: a {@link Version} with its build number, tag name, and last push date. */
  public record TemurinVersion(Version version, int build, String tag, Instant lastPushed) implements Comparable<TemurinVersion> {
    /** Convenience accessor for the major version. */
    public int major() {
      return version.major();
    }

    /** Convenience accessor for the Java version string. */
    public String javaVersion() {
      return version.javaVersion();
    }

    @Override
    public int compareTo(TemurinVersion o) {
      int c = version.compareTo(o.version);
      if (c == 0) c = Integer.compare(build, o.build);
      return c;
    }
  }

  private final DockerHubScanner scanner;

  /** Creates a new TemurinVersions scanner.
   * @param username the Docker Hub username, or {@code null} to disable authentication.
   * @param secret the Docker Hub password or Personal Access Token. */
  public TemurinVersions(String username, String secret) {
    this.scanner = new DockerHubScanner(username, secret);
  }

  /** Finds available Temurin JRE versions on Docker Hub, optionally stopping early.
   * <BR>Tags are fetched with {@code ordering=last_updated}, so the most recent tags come first.
   * When {@code since} is provided, pagination stops as soon as a tag older than {@code since} is encountered.
   * <BR>When several builds exist for the same Java version, only the highest build number is kept.
   * @param since only include tags pushed after this instant (exclusive). If {@code null}, all tags are included.
   * @param minMajor the minimum Java major version to include (e.g. 11), or 0 for no minimum.
   * @param maxMajor the maximum Java major version to include, or Integer.MAX_VALUE for no maximum.
   * @param shouldStop a predicate evaluated after each page with the accumulated results so far;
   *   if it returns {@code true}, pagination stops immediately. May be {@code null} to never stop early.
   * @return a list of Temurin versions, in no particular order.
   * @throws IOException if an HTTP error occurs.
   * @throws InterruptedException if the request is interrupted. */
  public List<TemurinVersion> findTemurinVersions(Instant since, int minMajor, int maxMajor, Predicate<List<TemurinVersion>> shouldStop) throws IOException, InterruptedException {
    List<TemurinVersion> versions = scanner.getVersions("library", "eclipse-temurin",
      "last_updated",
      tag -> TEMURIN_TAG.matcher(tag).matches(),
      t -> parseTemurinTag(t, since, minMajor, maxMajor),
      shouldStop
    );
    // A semantic Java version could theoretically have several Temurin builds. Keep the highest build number.
    Map<Version, TemurinVersion> best = new HashMap<>();
    for (TemurinVersion version : versions) {
      best.merge(
        version.version(),
        version,
        (a, b) -> a.build() >= b.build() ? a : b
      );
    }
    return new ArrayList<>(best.values());
  }

  /** Builds the JSON build matrix of new versions to publish.
   * <BR>For each major Java version >= {@code minMajor}, the most recent Temurin JRE tag is selected.
   * A version is considered already published if {@code jreVersionExtractor} returns a {@link Version}
   * that matches the Temurin version's Java version.
   * @param targetImage the target Docker Hub image (namespace/repository).
   * @param minMajor the minimum Java major version to include (e.g. 11), or 0 for no minimum.
   * @param maxMajor the maximum Java major version to include (e.g. 25), or Integer.MAX_VALUE for no maximum.
   * @param since only include Temurin tags pushed after this instant (exclusive). If {@code null},
   *   the last push date of the target repository (filtered by {@code publishedTagFilter}) is used.
   * @param publishedTagFilter a predicate that selects which published tags to consider, or {@code null} for all.
   * @param jreVersionExtractor a function that parses a published tag name into a {@link Version},
   *   returning {@code null} if the tag doesn't contain a JRE version.
   * @param tagBuilder a function that builds the target tag name from a {@link Version}
   *   (e.g. {@code v -> "1.1.0-jre-" + v.javaVersion()}).
   * @return a JSON array node containing the build matrix.
   * @throws IOException if an HTTP error occurs.
   * @throws InterruptedException if the request is interrupted. */
  public ArrayNode buildMatrix(String targetImage, int minMajor, int maxMajor, Instant since,
      Predicate<String> publishedTagFilter, Function<String, Version> jreVersionExtractor,
      Function<Version, String> tagBuilder) throws IOException, InterruptedException {
    int slash = targetImage.indexOf('/');
    if (slash <= 0 || slash == targetImage.length() - 1) {
      throw new IllegalArgumentException("Docker Hub image must be namespace/repository: " + targetImage);
    }

    String namespace = targetImage.substring(0, slash);
    String repository = targetImage.substring(slash + 1);

    Predicate<String> filter = publishedTagFilter != null ? publishedTagFilter : name -> true;
    Set<String> publishedTags = new HashSet<>(scanner.getVersions(namespace, repository, null, filter, DockerHubScanner.Tag::name, null));
    Set<Version> alreadyPublished = new HashSet<>();
    for (String tag : publishedTags) {
      Version version = jreVersionExtractor.apply(tag);
      if (version != null) {
        alreadyPublished.add(version);
      }
    }

    since = since == null ? scanner.getLastPushed(namespace, repository, publishedTagFilter) : since;
    List<TemurinVersion> available = findTemurinVersions(since, minMajor, maxMajor, null);

    // Keep only the highest version per major (don't rely on HashMap iteration order, compare versions explicitly).
    Map<Integer, TemurinVersion> latestPerMajor = new TreeMap<>();
    for (TemurinVersion version : available) {
      latestPerMajor.merge(version.major(), version,
        (a, b) -> b.version().compareTo(a.version()) > 0 ? b : a
      );
    }

    List<TemurinVersion> newVersions = latestPerMajor.values().stream()
      .filter(v -> !alreadyPublished.contains(v.version()))
      .sorted().toList();


    ArrayNode result = JSON.createArrayNode();
    for (TemurinVersion version : newVersions) {
      result.addObject()
        .put("javaVersion", version.javaVersion())
        .put("temurinTag", version.tag())
        .put(
          "baseImage",
          TEMURIN_REPOSITORY + ":" + version.tag()
        )
        .put(
          "targetImage",
          targetImage + ":" + tagBuilder.apply(version.version())
        );
    }

    return result;
  }

  /** Parses a Temurin tag into a {@link TemurinVersion}, or returns {@code null} if the tag is filtered out.
   * @param tag the tag with its name and last push date.
   * @param since the cutoff instant (exclusive), or {@code null} to accept all tags.
   * @param minMajor the minimum Java major version to include, or 0 for no minimum.
   * @param maxMajor the maximum Java major version to include, or Integer.MAX_VALUE for no maximum.
   * @return the parsed Temurin version, or {@code null} if the tag is too old or the major version is out of range. */
  private static TemurinVersion parseTemurinTag(DockerHubScanner.Tag tag, Instant since, int minMajor, int maxMajor) {
    if (since != null && tag.lastPushed() != null && !tag.lastPushed().isAfter(since)) {
      return null;
    }
    Matcher matcher = TEMURIN_TAG.matcher(tag.name());
    matcher.matches();
    int major = Integer.parseInt(matcher.group(1));
    if (major < minMajor || major > maxMajor) {
      return null;
    }
    return new TemurinVersion(
      new Version(major, Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))),
      Integer.parseInt(matcher.group(4)),
      tag.name(),
      tag.lastPushed()
    );
  }
}
