package com.fathzer.jdbbackup.cron.plugindownloader;

import java.net.URI;
import java.util.Map;

import lombok.Getter;

/** The class that represents a plugin repository json object.
 */
@Getter
public class RepositoryRecord {
	/** The class that represents a plugin repository.*/
	@Getter
	public static class Repository {
		private Map<String, URI> destinationManagers;
		private Map<String, URI> sourceManagers;
	}
	
	private Repository repository;
}
