package com.fathzer.jdbbackup.cron.plugindownloader;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.plugindownloader.RegistryRecord.Registry;
import com.fathzer.jdbbackup.utils.AbstractPluginsDownloader;
import com.fathzer.jdbbackup.utils.Cache;
import com.fathzer.jdbbackup.utils.ProxySettings;

/** A class that loads plugins from Internet remote repository.
 */
public class PluginsDownloader {
	/** The root URI of remote plugin registry. */
	private static final URI REGISTRY_ROOT_URI = URI.create(System.getProperty("pluginRegistry", "https://jdbbackup.github.io/web/registry/"));
	/** The directory where downloaded plugins are stored. */
	public static final Path DOWNLOAD_DIR = Paths.get(System.getProperty("downloadedPlugins", "downloadedPlugins"));
	private static final boolean CLEAR_DOWNLOAD_DIR = Boolean.getBoolean("clearDownloadedPlugins");
	
	private final AbstractPluginsDownloader destManagerDownloader;
	private final AbstractPluginsDownloader srcManagerDownloader;
	
	/** Constructor.
	 * @param proxy A proxy to use to connect to the remote registry (null if no proxy is required).
	 * @param version The version of plugins client.
	 */
	public PluginsDownloader(ProxySettings proxy, String version) {
		final URI uri = REGISTRY_ROOT_URI.resolve(version+".json");
		final Cache<Registry> cache = new Cache<>(); 
		this.destManagerDownloader = new SharedRegistryDownloader(JDbBackup.getDestinationManagers(), uri, DOWNLOAD_DIR.resolve("destinations"), cache) {
			@Override
			protected Map<String, URI> getURIMap(Registry registry) {
				return registry.getDestinationManagers();
			}
		};
		this.destManagerDownloader.setPluginTypeWording("destination manager");
		this.destManagerDownloader.setProxy(proxy);
		this.srcManagerDownloader = new SharedRegistryDownloader(JDbBackup.getSourceManagers(), uri, DOWNLOAD_DIR.resolve("sources"), cache) {
			@Override
			protected Map<String, URI> getURIMap(Registry registry) {
				return registry.getSourceManagers();
			}
		};
		this.srcManagerDownloader.setPluginTypeWording("source manager");
		this.srcManagerDownloader.setProxy(proxy);
	}
	
	private void clean() throws IOException {
		destManagerDownloader.clean();
		srcManagerDownloader.clean();
	}

	/** Search for missing source and destination managers in remote registry, then loads them and verify they are not missing anymore.
	 * @param missingSourceManagers The missing source managers
	 * @param missingDestManagers The missing destination managers
	 * @throws IOException If something went wrong
	 */
	public void load(Set<String> missingSourceManagers, Set<String> missingDestManagers) throws IOException {
		if (CLEAR_DOWNLOAD_DIR) {
			clean();
		}
		this.srcManagerDownloader.load(missingSourceManagers);
		this.destManagerDownloader.load(missingDestManagers);
	}
}
