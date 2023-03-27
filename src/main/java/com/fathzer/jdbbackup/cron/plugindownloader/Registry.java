package com.fathzer.jdbbackup.cron.plugindownloader;

import java.net.URI;
import java.util.Map;

import lombok.Getter;

@Getter
class RegistryRecord {
	@Getter
	static class Registry {
		private Map<String, URI> destinationManagers;
		private Map<String, URI> sourceManagers;
	}
	
	private Registry registry;
}
