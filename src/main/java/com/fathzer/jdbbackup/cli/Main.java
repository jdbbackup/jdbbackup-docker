package com.fathzer.jdbbackup.cli;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.utils.Files;

public class Main {
	private static final int OK = 0;
	private static final int ERROR = 1;
	private static final int WRONG_ARG = 2;
	
	private static final Logger LOG = LoggerFactory.getLogger(Main.class);
	
	public static void main(String... args) throws Exception {
		System.exit(new Main().doIt(args));
	}
	
	private int doIt(String... args) {
		if (args.length!=1) {
			LOG.error("Wrong number of arguments, only one is required");
			return WRONG_ARG;
		}
		final String dir = System.getProperty("pluginsDir");
		if (dir!=null) {
			File file = new File(dir);
			try {
				final URL[] urls = Files.getJarURL(file, 1);
				if (urls.length>0) {
					LOG.info("Loading plugins in {}", Arrays.asList(urls));
					JDbBackup.loadPlugins(URLClassLoader.newInstance(urls));
				} else {
					LOG.info("No external plugins in {}", file);
				}
			} catch (IOException e) {
				LOG.error("An error occurred", e);
				return ERROR;
			}
		}
		LOG.info("Available data base dumpers:");
		JDbBackup.getDBDumpers().forEach(dd -> LOG.info("  . {} -> {}",dd.getScheme(),dd.getClass().getName()));
		LOG.info("Available destination managers:");
		JDbBackup.getDestinationManagers().forEach(dm -> LOG.info("  . {} -> {}",dm.getScheme(),dm.getClass().getName()));
		return OK;
	}
}
