package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fathzer.jdbbackup.cron.parameters.Parameters.Task;

import lombok.extern.slf4j.Slf4j;

/** The main class.
 */
@Slf4j
public class Main {
	private static final int OK = 0;
	private static final int ERROR = 1;
	private static final int WRONG_ARG = 2;
	
	/** The main method.
	 * @param args Program arguments
	 */
	public static void main(String... args) {
		log.info("Starting {} version {}",Main.class.getSimpleName(),getVersion());
		int exitCode = OK;
		if (args.length>1) {
			log.error("Wrong number of arguments, can't be more than one");
			exitCode = WRONG_ARG;
		} else {
			try {
				new Main().doIt(args);
			} catch (IllegalArgumentException e) {
				log.error(e.getMessage(), e);
				exitCode = ERROR;
			} catch (Exception e) {
				log.error("An error occurred", e);
				exitCode = ERROR;
			}
		}
		if (exitCode!=OK) {
			System.exit(exitCode);
		}
	}
	
	private void doIt(String... args) throws IOException {
		final Configuration conf = getConfiguration(args.length==1?args[0]:null);
		new PluginsManager(getVersion()).load(conf);
		conf.getTasks().forEach(this::check);
		conf.schedule();
	}
	
	private void check(Task task) {
		task.getDestinations().forEach(this::checkDestination);
		//TODO How to check DB seems ok?
	}

	private void checkDestination(String destination) {
//TODO		JDbBackup.getDestinationManagers().get(destination)
	}

	private Configuration getConfiguration(String confFilePath) {
		try {
			return new Configuration(getTasksFile(confFilePath));
		} catch (IOException | IllegalArgumentException e) {
			log.error("Something is wrong with the parameters");
			throw new IllegalArgumentException(e);
		}
	}
	
	private static String getVersion() {
		try (InputStream stream = Main.class.getResourceAsStream("/version.txt")) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
	
	private static Path getTasksFile(String filePath) throws IOException {
		if (filePath==null) {
			filePath = System.getenv("TASKS_PATH"); //$NON-NLS-1$
			if (filePath==null) {
				filePath = "tasks.json"; //$NON-NLS-1$
			}
		}
		Path file = Paths.get(filePath);
		if (!Files.exists(file)) {
			throw new IOException("File "+filePath+" does not exists");
		} else if (!Files.isRegularFile(file)) {
			throw new IOException("File "+filePath+" is not a file");
		} else if (!Files.isReadable(file)) {
			throw new IOException("Can't read file "+filePath);
		}
		return file;
	}

}
