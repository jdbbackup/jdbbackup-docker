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
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.DBDumper;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.plugindownloader.RegistryRecord.Registry;
import com.fathzer.jdbbackup.utils.ProxySettings;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class PluginsDownloader {
	private static final String REGISTRY_ROOT_URI = System.getProperty("pluginRegistry", "https://jdbbackup.github.io/webtest/registry/");
	private static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("downloadedPlugins", "downloadedPlugins"));
	private static final boolean CLEAR_DOWNLOAD_DIR = Boolean.getBoolean("clearDownloadedPlugins");
	
	private ProxySettings proxy;
	private String version;
	
	private HttpClient httpClient;
	
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
		return Files.find(path, 0, (p, bfa) -> bfa.isRegularFile());
	}

	public void load(Set<String> missingDumpers, Set<String> missingManagers) throws IOException {
		if (CLEAR_DOWNLOAD_DIR) {
			clean();
		}
		final Registry registry = getRegistry();
		checkMissing(missingDumpers, registry.getDumpers(), "dumpers");
		checkMissing(missingManagers, registry.getManagers(), "managers");
		if (!Files.exists(DOWNLOAD_DIR)) {
			Files.createDirectories(DOWNLOAD_DIR);
		}
		final Set<URI> toDownload = Stream.concat(missingDumpers.stream().map(registry.getDumpers()::get), missingManagers.stream().map(registry.getManagers()::get)).collect(Collectors.toSet());
		try {
			toDownload.stream().forEach(this::download);
			load();
			verify(missingDumpers, missingManagers);
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}
	
	private void verify(Set<String> missingDumpers, Set<String> missingManagers) {
		//TODO core should give access to its internal plugin registries
		JDbBackup.getDBDumpers().stream().map(DBDumper::getScheme);
		
	}

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
		log.info("Start loading registy plugins");
		JDbBackup.loadPlugins(new URLClassLoader(urls));
	}
	
	private Path download(URI uri) {
		final Path file = DOWNLOAD_DIR.resolve(Paths.get(uri.getPath()).getFileName());
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
		return file;
	}
	
	private void checkMissing(Set<String> schemes, Map<String, ?> schemeToPath, String wording) {
		final Set<String> missing = schemes.stream().filter(s -> !schemeToPath.containsKey(s)).collect(Collectors.toSet());
		if (!missing.isEmpty()) {
			throw new IllegalArgumentException("Unable to find the following "+wording+": "+missing);
		}
	}
	
	private Registry getRegistry() throws IOException {
		final URI url = URI.create(REGISTRY_ROOT_URI+version+".json");
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
