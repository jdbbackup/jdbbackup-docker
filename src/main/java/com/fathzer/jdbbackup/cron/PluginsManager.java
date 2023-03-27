package com.fathzer.jdbbackup.cron;

import static com.fathzer.jdbbackup.DestinationManager.URI_PATH_SEPARATOR;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.fathzer.jdbbackup.SourceManager;
import com.fathzer.jdbbackup.DestinationManager;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.parameters.Parameters;
import com.fathzer.jdbbackup.cron.plugindownloader.PluginsDownloader;
import com.fathzer.jdbbackup.utils.Files;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class PluginsManager {
	private String version;

	PluginsManager(String version) {
		super();
		this.version = version;
	}

	private void load() throws IOException {
		final String dir = System.getenv("pluginsDirectory");
		if (dir != null) {
			final File file = new File(dir);
			final URL[] urls = Files.getJarURL(file, 1);
			if (urls.length > 0) {
				log.info("Loading plugins in {}", Arrays.asList(urls));
				JDbBackup.loadPlugins(URLClassLoader.newInstance(urls));
			} else {
				log.info("No external plugins in {}", file);
			}
		}
	}
	
	void load(Configuration conf) throws IOException {
		load();
		log.info("Available source managers:");
		final Map<String, SourceManager> srcManager = JDbBackup.getSourceManagers().getLoaded();
		srcManager.values().forEach(dd -> log.info("  . {} -> {}", dd.getScheme(), dd.getClass().getName()));
		log.info("Available destination managers:");
		@SuppressWarnings("rawtypes")
		final Map<String, DestinationManager> managers = JDbBackup.getDestinationManagers().getLoaded();
		managers.values().forEach(dm -> log.info("  . {} -> {}", dm.getScheme(), dm.getClass().getName()));
		
		final Set<String> missingSrcMngr = conf.getTasks().stream().map(Parameters.Task::getSource).map(this::getScheme).filter(s -> !srcManager.containsKey(s)).collect(Collectors.toSet());
		final Set<String> missingDestMngr = conf.getTasks().stream().flatMap(task -> task.getDestinations().stream()).map(this::getScheme).filter(s -> !managers.containsKey(s)).collect(Collectors.toSet());
		if (!missingSrcMngr.isEmpty()) {
			log.info("Source managers to search on registry: {}",missingSrcMngr);
		}
		if (!missingDestMngr.isEmpty()) {
			log.info("Destination managers to search on registry: {}",missingDestMngr);
		}
		if (!missingSrcMngr.isEmpty() || !missingDestMngr.isEmpty()) {
			new PluginsDownloader(conf.getProxy(), version).load(missingSrcMngr, missingDestMngr);
		}
	}

	private String getScheme(String address) {
		if (address==null) {
			throw new IllegalArgumentException();
		}
		int index = address.indexOf(':');
		if (index<=0) {
			throw new IllegalArgumentException ("Destination scheme is missing in "+address);
		}
		final String result = address.substring(0, index);
		for (int i=1;i<=2;i++) {
			if (index+i>=address.length() || address.charAt(index+i)!=URI_PATH_SEPARATOR) {
				throw new IllegalArgumentException("Address has not the right format: "+address+" does not not match scheme://path");
			}
		}
		return result;
	}
}
