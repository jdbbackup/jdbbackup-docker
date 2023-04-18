package com.fathzer.jdbbackup.cron;

import static com.fathzer.jdbbackup.DestinationManager.URI_PATH_SEPARATOR;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fathzer.jdbbackup.SourceManager;
import com.fathzer.jdbbackup.DestinationManager;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.parameters.Parameters;
import com.fathzer.jdbbackup.cron.plugindownloader.SharedRepositoryDownloader;
import com.fathzer.jdbbackup.cron.plugindownloader.RepositoryRecord.Repository;
import com.fathzer.jdbbackup.utils.AbstractManagersDownloader;
import com.fathzer.jdbbackup.utils.Cache;
import com.fathzer.plugin.loader.jar.JarPluginLoader;
import com.fathzer.plugin.loader.utils.FileUtils;
import com.fathzer.plugin.loader.utils.ProxySettings;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class PluginsManager {
	/** The root URI of remote plugin repository. */
	private static final URI REPOSITORY_ROOT_URI = URI.create(System.getProperty("pluginRepository", "https://jdbbackup.github.io/web/repository/"));
	/** The directory where downloaded plugins are stored. */
	public static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("downloadedPlugins", System.getProperty("user.home", "")),".jdbbackup");
	private static final boolean CLEAR_DOWNLOAD_DIR = Boolean.getBoolean("clearDownloadedPlugins");

	private SharedRepositoryDownloader destManagerDownloader;
	private SharedRepositoryDownloader srcManagerDownloader;

	/** Constructor.
	 * @param version The version of plugins client.
	 */
	PluginsManager(String version) {
		super();
		final URI uri = REPOSITORY_ROOT_URI.resolve(version+".json");
		final Cache<Repository> cache = new Cache<>(); 
		this.destManagerDownloader = new SharedRepositoryDownloader(uri, DOWNLOAD_DIR.resolve("destinations"), cache, Repository::getDestinationManagers);
		this.destManagerDownloader.setPluginTypeWording("destination manager");
		this.srcManagerDownloader = new SharedRepositoryDownloader(uri, DOWNLOAD_DIR.resolve("sources"), cache, Repository::getSourceManagers);
		this.srcManagerDownloader.setPluginTypeWording("source manager");
	}

	private void load(JDbBackup backup) throws IOException {
		final String dir = System.getenv("pluginsDirectory");
		if (dir != null) {
			final Path file = Paths.get(dir);
			final List<Path> files = FileUtils.getJarFiles(file, 1);
			if (!files.isEmpty()) {
				log.info("Loading plugins in {}", files);
				load(backup, files);
			} else {
				log.info("Found no plugin in {}", file);
			}
		}
	}
	
	private void load(JDbBackup backup, List<Path> files) {
		final JarPluginLoader loader = new JarPluginLoader();
		for (Path file:files) {
			try {
				loader.getPlugins(file, SourceManager.class).forEach(s -> backup.getSourceManagers().put(s.getScheme(), s));
				loader.getPlugins(file, DestinationManager.class).forEach(d -> backup.getDestinationManagers().put(d.getScheme(), d));
			} catch (IOException e) {
				log.error("Error while loading plugins in "+file,e);
			}
		}
	}
	
	void load(JDbBackup backup, Configuration conf) throws IOException {
		load(backup);
		load(backup.getSourceManagers(), SourceManager::getScheme, SourceManager.class, conf.getTasks().stream().map(Parameters.Task::getSource).map(this::getScheme),
				"source managers", this.srcManagerDownloader, conf.getProxy());
		load(backup.getDestinationManagers(), DestinationManager::getScheme, DestinationManager.class, conf.getTasks().stream().flatMap(task -> task.getDestinations().stream()).map(this::getScheme),
				"destination managers", this.destManagerDownloader, conf.getProxy());
	}
	
	private <T> void load(Map<String, T> registry, Function<T,String> keyFunction, Class<T> pluginClass, Stream<String> requiredKeys, String wording, AbstractManagersDownloader downloader, ProxySettings proxy) throws IOException {
		if (CLEAR_DOWNLOAD_DIR) {
			downloader.clean();
		}
		final Set<String> missing = getMissing(registry, requiredKeys, wording);
		downloader.setProxy(proxy);
		final Collection<Path> files = downloader.download(missing.toArray(String[]::new));
		for (Path file: files) {
			final JarPluginLoader loader = new JarPluginLoader();
			final List<T> plugins = loader.getPlugins(file, pluginClass);
			if (plugins.isEmpty()) {
				log.warn("Found no {} in file {}",wording, file);
			} else {
				register(registry, keyFunction, plugins, file, wording);
			}
		}
	}

	private <T> void register(Map<String, T> registry, Function<T,String> keyFunction, final List<T> plugins, Path file, String wording) {
		final List<T> added = plugins.stream().filter(p -> !registry.containsKey(keyFunction.apply(p))).collect(Collectors.toList());
		added.forEach(p -> registry.put(keyFunction.apply(p), p));
		if (!added.isEmpty()) {
			log.info("{} loaded from file {}",capitalize(wording), file);
			added.forEach(dd -> log.info("  . {} -> {}", keyFunction.apply(dd), dd.getClass().getName()));
		}
	}
	
	<T> Set<String> getMissing(Map<String, T> managers, Stream<String> requiredKeys, String wording) {
		log.info(managers.isEmpty()?"No {} installed":"Installed {}:", wording);
		managers.entrySet().forEach(e -> log.info("  . {} -> {}", e.getKey(), e.getValue().getClass().getName()));
		final Set<String> missing = requiredKeys.filter(s -> !managers.containsKey(s)).collect(Collectors.toSet());
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
