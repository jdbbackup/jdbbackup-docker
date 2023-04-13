package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import com.fathzer.jdbbackup.cron.plugindownloader.RepositoryRecord.Repository;
import com.fathzer.jdbbackup.utils.AbstractManagersDownloader;
import com.fathzer.jdbbackup.utils.Cache;
import com.fathzer.plugin.loader.utils.ProxySettings;

/** A class that loads plugins from Internet remote repository.
 */
public class PluginsDownloader {
	/** The root URI of remote plugin repository. */
	private static final URI REPOSITORY_ROOT_URI = URI.create(System.getProperty("pluginRepository", "https://jdbbackup.github.io/web/epository/"));
	/** The directory where downloaded plugins are stored. */
	public static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("downloadedPlugins", "downloadedPlugins"));
	private static final boolean CLEAR_DOWNLOAD_DIR = Boolean.getBoolean("clearDownloadedPlugins");
	
	private final AbstractManagersDownloader destManagerDownloader;
	private final AbstractManagersDownloader srcManagerDownloader;
	
	/** Constructor.
	 * @param proxy A proxy to use to connect to the remote repository (null if no proxy is required).
	 * @param version The version of plugins client.
	 */
	public PluginsDownloader(ProxySettings proxy, String version) {
		final URI uri = REPOSITORY_ROOT_URI.resolve(version+".json");
		final Cache<Repository> cache = new Cache<>(); 
		this.destManagerDownloader = new SharedRepositoryDownloader(uri, DOWNLOAD_DIR.resolve("destinations"), cache) {
			@Override
			protected Map<String, URI> getURIMap(Repository repository) {
				return repository.getDestinationManagers();
			}
		};
		this.destManagerDownloader.setPluginTypeWording("destination manager");
		this.destManagerDownloader.setProxy(proxy);
		this.srcManagerDownloader = new SharedRepositoryDownloader(uri, DOWNLOAD_DIR.resolve("sources"), cache) {
			@Override
			protected Map<String, URI> getURIMap(Repository repository) {
				return repository.getSourceManagers();
			}
		};
		this.srcManagerDownloader.setPluginTypeWording("source manager");
		this.srcManagerDownloader.setProxy(proxy);
	}
	
	private void clean() throws IOException {
		destManagerDownloader.clean();
		srcManagerDownloader.clean();
	}

	/** Search for missing source and destination managers in remote repository, then loads them and verify they are not missing anymore.
	 * @param missingSourceManagers The missing source managers
	 * @param missingDestManagers The missing destination managers
	 * @throws IOException If something went wrong
	 */
	public void load(Set<String> missingSourceManagers, Set<String> missingDestManagers) throws IOException {
		if (CLEAR_DOWNLOAD_DIR) {
			clean();
		}
		this.srcManagerDownloader.download(missingSourceManagers.toArray(String[]::new));
		this.destManagerDownloader.download(missingDestManagers.toArray(String[]::new));
	}
}
