package com.fathzer.jdbbackup.cron.plugindownloader;

import java.net.URI;
import java.util.Map;

import lombok.Getter;

@Getter
class RegistryRecord {
	@Getter
	static class Registry {
		private Map<String, URI> managers;
		private Map<String, URI> dumpers;
	}
	
	private Registry registry;
}
