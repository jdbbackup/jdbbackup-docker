package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest.Builder;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.cron.plugindownloader.RepositoryRecord.Repository;
import com.fathzer.jdbbackup.utils.AbstractManagersDownloader;
import com.fathzer.jdbbackup.utils.Cache;

import lombok.Setter;

abstract class SharedRepositoryDownloader extends AbstractManagersDownloader {
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
	private Cache<Repository> cache;

	protected SharedRepositoryDownloader(URI uri, Path localDirectory, Cache<Repository> cache) {
		super(uri, localDirectory);
		this.cache = cache;
	}

	@Override
	protected Map<String, URI> getURIMap(InputStream in) throws IOException {
		try {
			decoder.setIn(in);
			final Repository registry = cache.get(decoder);
			return getURIMap(registry);
		} catch (UncheckedIOException e) {
			throw e;
		}
	}

	protected abstract Map<String, URI> getURIMap(final Repository registry);

	@Override
	protected Builder getRepositoryRequestBuilder() {
		final Builder builder = super.getRepositoryRequestBuilder();
		builder.header("Accept","application/json");
		return builder;
	}
}
