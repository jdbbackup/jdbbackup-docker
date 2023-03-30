package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.cron.plugindownloader.RegistryRecord.Registry;
import com.fathzer.jdbbackup.utils.AbstractPluginsDownloader;
import com.fathzer.jdbbackup.utils.Cache;
import com.fathzer.jdbbackup.utils.PluginRegistry;

import lombok.Setter;

abstract class SharedRegistryDownloader extends AbstractPluginsDownloader {
	private static class RegistryDecoder implements Supplier<Registry> {
		@Setter
		private InputStream in;
		
		public Registry get() {
			try {
				return new ObjectMapper().readValue(in, RegistryRecord.class).getRegistry();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
	
	private static final RegistryDecoder decoder = new RegistryDecoder();
	private Cache<Registry> cache;

	protected SharedRegistryDownloader(PluginRegistry<?> registry, URI uri, Path localDirectory, Cache<Registry> cache) {
		super(registry, uri, localDirectory);
		this.cache = cache;
	}

	@Override
	protected Map<String, URI> getURIMap(InputStream in) throws IOException {
		try {
			decoder.setIn(in);
			final Registry registry = cache.get(decoder);
			return getURIMap(registry);
		} catch (UncheckedIOException e) {
			throw e;
		}
	}

	protected abstract Map<String, URI> getURIMap(final Registry registry);

	@Override
	protected void customizeRegistryRequest(HttpRequest.Builder requestBuilder) {
		requestBuilder.header("Accept","application/json");
	}
}
