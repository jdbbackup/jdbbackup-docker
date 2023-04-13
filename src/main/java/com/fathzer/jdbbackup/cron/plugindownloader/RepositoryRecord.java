package com.fathzer.jdbbackup.cron.plugindownloader;

import java.net.URI;
import java.util.Map;

import lombok.Getter;

@Getter
class RepositoryRecord {
	@Getter
	static class Repository {
		private Map<String, URI> destinationManagers;
		private Map<String, URI> sourceManagers;
	}
	
	private Repository repository;
}
