package com.fathzer.jdbbackup.cron;

import static com.fathzer.jdbbackup.DestinationManager.URI_PATH_SEPARATOR;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fathzer.jdbbackup.SourceManager;
import com.fathzer.jdbbackup.DestinationManager;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.parameters.Parameters;
import com.fathzer.jdbbackup.cron.plugindownloader.PluginsDownloader;
import com.fathzer.plugin.loader.jar.JarPluginLoader;
import com.fathzer.plugin.loader.utils.FileUtils;
import com.fathzer.plugin.loader.utils.PluginRegistry;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class PluginsManager {
	private String version;

	PluginsManager(String version) {
		super();
		this.version = version;
	}

	private void load(JDbBackup backup) throws IOException {
		//TODO Verify it works, seems jar are not directly uder pluginsDirectory
		final String dir = System.getenv("pluginsDirectory");
		if (dir != null) {
			final Path file = Paths.get(dir);
			final List<Path> files = FileUtils.getJarFiles(file, 1);
			if (!files.isEmpty()) {
				log.info("Loading plugins in {}", files);
				load(backup, files);
			} else {
				log.info("No external plugins in {}", file);
			}
		}
	}
	
	private void load(JDbBackup backup, List<Path> files) {
		final JarPluginLoader loader = new JarPluginLoader();
		for (Path file:files) {
			try {
				backup.getSourceManagers().registerAll(loader.getPlugins(file, SourceManager.class));
				backup.getDestinationManagers().registerAll(loader.getPlugins(file, DestinationManager.class));
			} catch (IOException e) {
				log.error("Error while loading plugins in "+file,e);
			}
		}
	}
	
	void load(JDbBackup backup, Configuration conf) throws IOException {
		load(backup);
		final Set<String> missingSrcMngr = getMissing(backup.getSourceManagers(), conf.getTasks().stream().map(Parameters.Task::getSource).map(this::getScheme), "source managers");
		final Set<String> missingDestMngr = getMissing(backup.getDestinationManagers(), conf.getTasks().stream().flatMap(task -> task.getDestinations().stream()).map(this::getScheme), "destination managers");
		if (!missingSrcMngr.isEmpty() || !missingDestMngr.isEmpty()) {
			new PluginsDownloader(conf.getProxy(), version).load(missingSrcMngr, missingDestMngr);
		}
	}
	
	<T> Set<String> getMissing(PluginRegistry<T> registry, Stream<String> requiredKeys, String wording) {
		log.info("Installed {}:", wording);
		final Map<String, T> srcManager = registry.getRegistered();
		srcManager.values().forEach(dd -> log.info("  . {} -> {}", registry.getKeyFunction(), dd.getClass().getName()));
		final Set<String> missing = requiredKeys.filter(s -> !srcManager.containsKey(s)).collect(Collectors.toSet());
		if (!missing.isEmpty()) {
			log.info("{} to search in repository: {}", capitalize(wording), missing);
		}
		return missing;
	}
	
	static String capitalize(String str) {
	    if (str == null || str.length()<=1) {
	    	return str;
	    }
	    return str.substring(0, 1).toUpperCase() + str.substring(1);
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
