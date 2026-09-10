package com.fathzer.docker.java;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/** A generic Docker Hub API client that can list tags from any repository.
 * <BR>Authentication is optional but recommended to increase the Docker Hub API rate limit.
 * Use {@link #setCredentials(String, String)} to provide a Docker Hub username and password
 * (or Personal Access Token). The access token is obtained lazily on the first API call
 * and refreshed automatically when it expires.
 */
public class DockerHubScanner {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final String JSON_CONTENT_TYPE = "application/json";

  /** A Docker Hub tag with its name and last push date. */
  public record Tag(String name, Instant lastPushed) {
  }

  private final HttpClient http = HttpClient.newHttpClient();
  private final String username;
  private final String secret;
  private String accessToken;
  private Instant tokenExpiration;

  /** Creates a new scanner without authentication.
   * @param username the Docker Hub username, or {@code null} to disable authentication.
   * @param secret the Docker Hub password or Personal Access Token.
   */
  public DockerHubScanner(String username, String secret) {
    this.username = username;
    this.secret = secret;
  }

  /** Finds the most recent tag push date in a Docker Hub repository.
   * <BR>Uses {@code ordering=-last_updated} and stops after the first matching tag.
   * @param namespace the Docker Hub namespace (e.g. "jdbbackup").
   * @param repository the Docker Hub repository (e.g. "jdbbackup").
   * @param tagFilter a predicate that selects which tags to consider, or {@code null} to consider all tags.
   * @return the last push date of the most recent matching tag, or {@code null} if no tag matches.
   * @throws IOException if an HTTP error occurs.
   * @throws InterruptedException if the request is interrupted. */
  public Instant getLastPushed(String namespace, String repository, Predicate<String> tagFilter) throws IOException, InterruptedException {
    Predicate<String> filter = tagFilter != null ? tagFilter : name -> true;
    List<Instant> result = getVersions(namespace, repository, "-last_updated", filter, Tag::lastPushed, list -> !list.isEmpty());
    return result.isEmpty() ? null : result.get(0);
  }

  /** Fetches tags from a Docker Hub repository, filters them, and transforms them into versions.
   * <BR>Tags are fetched in the default order (most recent first when {@code ordering} is set to
   * {@code -last_updated}). Pagination stops early when {@code shouldStop} returns {@code true}.
   * @param namespace the Docker Hub namespace (e.g. "library" or "jdbbackup").
   * @param repository the Docker Hub repository (e.g. "eclipse-temurin" or "jdbbackup").
   * @param ordering the Docker Hub ordering parameter (e.g. "-last_updated"), or {@code null} for default.
   * @param tagFilter a predicate that selects which tags to keep (based on tag name).
   * @param tagMapper a function that transforms a {@link Tag} into a version object.
   * @param shouldStop a predicate evaluated after each page with the accumulated results so far;
   *   if it returns {@code true}, pagination stops immediately. May be {@code null} to never stop early.
   * @param <T> the version type.
   * @return a list of versions, in the order they appear on Docker Hub.
   * @throws IOException if an HTTP error occurs.
   * @throws InterruptedException if the request is interrupted. */
  public <T> List<T> getVersions(String namespace, String repository,
      String ordering, Predicate<String> tagFilter, Function<Tag, T> tagMapper,
      Predicate<List<T>> shouldStop) throws IOException, InterruptedException {

    List<T> result = new ArrayList<>();
    String url = buildTagsUrl(namespace, repository, ordering);
    if (shouldStop == null) {
      shouldStop = r -> false;
    }

    while (url != null) {
      JsonNode page = getJson(url);
      processTags(page, tagFilter, tagMapper, result);

      if (shouldStop.test(result)) {
        break;
      }

      url = getNextUrl(page);
    }

    return result;
  }

  /** Builds the Docker Hub tags URL for the given repository and ordering.
   * @param namespace the Docker Hub namespace.
   * @param repository the Docker Hub repository.
   * @param ordering the ordering parameter, or {@code null}.
   * @return the tags URL. */
  private static String buildTagsUrl(String namespace, String repository, String ordering) {
    String url = "https://hub.docker.com/v2/namespaces/" + namespace + "/repositories/" + repository + "/tags?page_size=100";
    if (ordering != null) {
      url += "&ordering=" + ordering;
    }
    return url;
  }

  /** Extracts the next page URL from a JSON response, or {@code null} if there is no next page.
   * @param page the JSON response page.
   * @return the next URL, or {@code null}. */
  private static String getNextUrl(JsonNode page) {
    JsonNode next = page.get("next");
    return next == null || next.isNull() ? null : next.asText();
  }

  /** Processes the tags from a JSON response page, filtering and mapping them into the result list.
   * @param page the JSON response page.
   * @param tagFilter the tag name filter.
   * @param tagMapper the tag-to-version mapper.
   * @param result the accumulated results (modified in place).
   * @param <T> the version type. */
  private static <T> void processTags(JsonNode page, Predicate<String> tagFilter, Function<Tag, T> tagMapper, List<T> result) {
    for (JsonNode tag : page.path("results")) {
      String name = tag.path("name").asText();
      if (tagFilter.test(name)) {
        T mapped = tagMapper.apply(parseTag(tag));
        if (mapped != null) {
          result.add(mapped);
        }
      }
    }
  }

  /** Parses a JSON tag node into a {@link Tag}.
   * @param tag the JSON tag node.
   * @return the parsed tag. */
  private static Tag parseTag(JsonNode tag) {
    String name = tag.path("name").asText();
    JsonNode pushed = tag.get("tag_last_pushed");
    Instant lastPushed = pushed != null && !pushed.isNull() ? Instant.parse(pushed.asText()) : null;
    return new Tag(name, lastPushed);
  }

  /** Performs an authenticated GET request, refreshing the access token on 401.
   * @param url the URL to fetch.
   * @return the JSON response body.
   * @throws IOException if the HTTP request fails or returns a non-200 status after retry.
   * @throws InterruptedException if the request is interrupted. */
  private JsonNode getJson(String url) throws IOException, InterruptedException {
    if (username != null && isTokenExpired()) {
      accessToken = obtainAccessToken();
    }

    HttpResponse<String> response = sendGet(url);

    // If the token expired unexpectedly, refresh it and retry once.
    if (response.statusCode() == 401 && username != null) {
      accessToken = obtainAccessToken();
      response = sendGet(url);
    }

    if (response.statusCode() != 200) {
      throw new IOException(
        "HTTP " + response.statusCode() + " from " + url + "\n" + response.body()
      );
    }

    return JSON.readTree(response.body());
  }

  /** Sends a GET request with the current access token (if any).
   * @param url the URL to fetch.
   * @return the HTTP response.
   * @throws IOException if the request fails.
   * @throws InterruptedException if the request is interrupted. */
  private HttpResponse<String> sendGet(String url) throws IOException, InterruptedException {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Accept", JSON_CONTENT_TYPE)
      .GET();

    if (accessToken != null) {
      builder.header("Authorization", "Bearer " + accessToken);
    }

    return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  /** Exchanges the username and secret for a short-lived access token via the Docker Hub auth endpoint.
   * <BR>The token's expiration time is extracted from the JWT {@code exp} claim and stored
   * in {@link #tokenExpiration}.
   * @return the access token.
   * @throws IOException if the auth request fails.
   * @throws InterruptedException if the request is interrupted. */
  private String obtainAccessToken() throws IOException, InterruptedException {
    String url = "https://hub.docker.com/v2/auth/token";
    String body = JSON.writeValueAsString(Map.of(
      "identifier", username,
      "secret", secret
    ));

    HttpRequest request = HttpRequest.newBuilder()
      .uri(URI.create(url))
      .header("Accept", JSON_CONTENT_TYPE)
      .header("Content-Type", JSON_CONTENT_TYPE)
      .POST(HttpRequest.BodyPublishers.ofString(body))
      .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new IOException(
        "HTTP " + response.statusCode() + " from auth endpoint\n" + response.body()
      );
    }

    JsonNode json = JSON.readTree(response.body());
    JsonNode tokenNode = json.get("access_token");
    if (tokenNode == null || tokenNode.isNull()) {
      throw new IOException("No access_token in auth response: " + response.body());
    }
    String token = tokenNode.asText();
    tokenExpiration = extractExpiration(token);
    return token;
  }

  /** Extracts the {@code exp} claim from a JWT token.
   * @param jwt the JWT token string.
   * @return the expiration instant, or {@code null} if the claim is absent or unparseable. */
  private static Instant extractExpiration(String jwt) {
    String[] parts = jwt.split("\\.");
    if (parts.length < 2) {
      return null;
    }
    byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
    try {
      JsonNode claims = JSON.readTree(payload);
      JsonNode exp = claims.get("exp");
      if (exp != null && exp.isNumber()) {
        return Instant.ofEpochSecond(exp.asLong());
      }
    } catch (IOException e) {
      // Ignore: will fall back to 401 retry
    }
    return null;
  }

  /** Returns true if the access token is null or will expire within 1 minute.
   * @return true if the token needs to be refreshed. */
  private boolean isTokenExpired() {
    if (accessToken == null) {
      return true;
    }
    if (tokenExpiration == null) {
      return false; // Can't determine expiration, rely on 401 retry
    }
    return Instant.now().plusSeconds(60).isAfter(tokenExpiration);
  }
}
