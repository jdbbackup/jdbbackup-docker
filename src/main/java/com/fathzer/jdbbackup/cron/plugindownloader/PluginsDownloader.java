package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Builder;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.plugindownloader.RegistryRecord.Registry;
import com.fathzer.jdbbackup.utils.ProxySettings;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PluginsDownloader {
	/** The root URI of remote plugin registry. */
	private static final URI REGISTRY_ROOT_URI = URI.create(System.getProperty("pluginRegistry", "https://jdbbackup.github.io/web/registry/"));
	/** The directory where downloaded plugins are stored. */
	public static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("downloadedPlugins", "downloadedPlugins"));
	private static final boolean CLEAR_DOWNLOAD_DIR = Boolean.getBoolean("clearDownloadedPlugins");
	
	private ProxySettings proxy;
	private String version;
	
	private HttpClient httpClient;
	
	/** Constructor.
	 * @param proxy A proxy to use to connect to the remote registry (null if no proxy is required).
	 * @param version The version of plugins client.
	 */
	public PluginsDownloader(ProxySettings proxy, String version) {
		this.proxy = proxy;
		this.version = version;
	}
	
	private void clean() throws IOException {
		if (Files.isDirectory(DOWNLOAD_DIR)) {
			log.info("Deleting plugins already downloaded in {}", DOWNLOAD_DIR);
			try (Stream<Path> files = getChildrenFiles(DOWNLOAD_DIR)) {
				final List<Path> toDelete = files.collect(Collectors.toList());
				for (Path p : toDelete) {
					Files.delete(p);
				}
			}
		}
	}

	private Stream<Path> getChildrenFiles(Path path) throws IOException {
		return Files.find(path, 1, (p, bfa) -> bfa.isRegularFile());
	}

	public void load(Set<String> missingDumpers, Set<String> missingManagers) throws IOException {
		if (CLEAR_DOWNLOAD_DIR) {
			clean();
		}
		final Registry registry = getRegistry();
		checkMissingKeys(missingDumpers, registry.getDumpers(), "dumpers");
		checkMissingKeys(missingManagers, registry.getManagers(), "managers");
		if (!Files.exists(DOWNLOAD_DIR)) {
			Files.createDirectories(DOWNLOAD_DIR);
		}
		final Set<URI> toDownload = Stream.concat(missingDumpers.stream().map(registry.getDumpers()::get), missingManagers.stream().map(registry.getManagers()::get)).collect(Collectors.toSet());
		try {
			toDownload.stream().forEach(this::download);
			load();
			checkMissingKeys(missingDumpers, JDbBackup.getDBDumpers().getLoaded(),"dumpers");
			checkMissingKeys(missingManagers, JDbBackup.getDestinationManagers().getLoaded(),"managers");
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	/** Loads all plugins available in the {@link #DOWNLOAD_DIR} directory
	 * @throws IOException if something went wrong.
	 */
	public void load() throws IOException {
		URL[] urls;
		try (Stream<Path> files = getChildrenFiles(DOWNLOAD_DIR)) {
			urls = files.map(f -> {
				try {
					return f.toUri().toURL();
				} catch (MalformedURLException e) {
					throw new UncheckedIOException(e);
			}}).toArray(URL[]::new);
		} catch (UncheckedIOException e) {
			throw e;
		}
		log.info("Start loading registy plugins from {}",Arrays.asList(urls));
		JDbBackup.loadPlugins(new URLClassLoader(urls));
		log.info("Registy plugins are loaded");
	}
	
	/** Downloads an URI to a file.
	 * @param uri The uri to download
	 * @param file 
	 * @return The path where the URI body was downloaded.
	 * @throws UncheckedIOException if something went wrong
	 */
	private Path download(URI uri) {
		final Path file = DOWNLOAD_DIR.resolve(Paths.get(uri.getPath()).getFileName());
		if (Files.exists(file)) {
			log.info("{} was already downloaded to file {}",uri, file);
		} else {
			log.info("Start downloading {} to file {}",uri, file);
			final HttpRequest request = getRequestBuilder().uri(uri).build();
			try {
				getHttpClient().send(request, BodyHandlers.ofFile(file));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new UncheckedIOException(new IOException(e));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
		return file;
	}
	
	/** Checks that plugins are registered in a map.
	 * @param keys The keys to check.
	 * @param map A map with String key in which to search
	 * @param wording a wording used for exception message generation
	 * @throws IllegalArgumentException if some keys are missing
	 */
	private <T> void checkMissingKeys(Set<T> keys, Map<T, ?> map, String wording) {
		final Set<T> missing = keys.stream().filter(s -> !map.containsKey(s)).collect(Collectors.toSet());
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException(String.format("Unable to find the following %s: %s", wording, missing));
		}
	}
	
	private Registry getRegistry() throws IOException {
		final URI url = REGISTRY_ROOT_URI.resolve(version+".json");
		log.info("Downloading plugin registry at {}",url);
		final HttpRequest request = getRequestBuilder().uri(url).build();
		try {
			final HttpResponse<InputStream> response = getHttpClient().send(request, BodyHandlers.ofInputStream());
			try (InputStream in = response.body()) {
				return new ObjectMapper().readValue(in, RegistryRecord.class).getRegistry();
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException(e);
		}
	}

	private java.net.http.HttpRequest.Builder getRequestBuilder() {
		java.net.http.HttpRequest.Builder builder = HttpRequest.newBuilder()
				  .version(HttpClient.Version.HTTP_2)
				  .GET();
		if (proxy!=null && proxy.getLogin()!=null) {
			final String login = proxy.getLogin().getUserName()+":"+String.valueOf(proxy.getLogin().getPassword());
			final String encoded = new String(Base64.getEncoder().encode(login.getBytes()));
            builder.setHeader("Proxy-Authorization", "Basic " + encoded);
		}
		return builder;
	}

	private HttpClient getHttpClient() {
		if (httpClient==null) {
			final Builder clientBuilder = HttpClient.newBuilder();
			if (proxy!=null) {
				clientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxy.getHost(), proxy.getPort())));
			}
			clientBuilder.followRedirects(Redirect.ALWAYS);
			this.httpClient = clientBuilder.build();
		}
		return httpClient;
	}
}
