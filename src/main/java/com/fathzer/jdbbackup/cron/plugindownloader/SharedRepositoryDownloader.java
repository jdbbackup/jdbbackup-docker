package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.cron.plugindownloader.RepositoryRecord.Repository;
import com.fathzer.jdbbackup.utils.AbstractManagersDownloader;
import com.fathzer.jdbbackup.utils.Cache;

import lombok.Setter;

/** A downloader that share a single underlying json to manage source or destination repository.
 */
public class SharedRepositoryDownloader extends AbstractManagersDownloader {
	private static class RepositoryDecoder implements Supplier<Repository> {
		@Setter
		private InputStream in;
		
		public Repository get() {
			try {
				return new ObjectMapper().readValue(in, RepositoryRecord.class).getRepository();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
	
	private static final RepositoryDecoder decoder = new RepositoryDecoder();
	private final Cache<Repository> cache;
	private final Function<Repository, Map<String,URI>> repoToUriMap;

	/** Constructor.
	 * @param uri The repository URI
	 * @param localDirectory The directory where to store the downloaded managers.
	 * @param cache A Cache to store the repository
	 * @param repoToUriMap A function that returns the repository (source or destination)
	 */
	public SharedRepositoryDownloader(URI uri, Path localDirectory, Cache<Repository> cache, Function<Repository, Map<String,URI>> repoToUriMap) {
		super(uri, localDirectory);
		this.cache = cache;
		this.repoToUriMap = repoToUriMap;
	}

	@Override
	protected Map<String, URI> getURIMap(InputStream in) throws IOException {
		try {
			decoder.setIn(in);
			final Repository repository = cache.get(decoder);
			return repoToUriMap.apply(repository);
		} catch (UncheckedIOException e) {
			throw e.getCause();
		}
	}

	@Override
	protected Builder getRepositoryRequestBuilder() {
		final Builder builder = super.getRepositoryRequestBuilder();
		builder.header("Accept","application/json");
		return builder;
	}
}
