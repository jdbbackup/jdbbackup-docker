package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import com.fathzer.jdbbackup.DestinationManager;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.SourceManager;
import com.fathzer.jdbbackup.cron.parameters.Task;

import lombok.extern.slf4j.Slf4j;

/** The main class.
 */
@Slf4j
public class Main {
	private static final int OK = 0;
	private static final int ERROR = 1;
	private static final int WRONG_ARG = 2;
	
	private JDbBackup backupEngine;
	
	/** Constructor.
	 * @param jDbBackup The instance used to perform backups
	 */
	public Main(JDbBackup jDbBackup) {
		this.backupEngine = jDbBackup;
	}

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
				new Main(new JDbBackup()).doIt(args);
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
		new PluginsManager(getVersion()).load(backupEngine, conf);
		conf.getTasks().forEach(this::check);
		conf.schedule(backupEngine);
	}
	
	private void check(Task task) {
		//TODO Unfortunately, SourceManager have no method to check source uri is correct. Maybe in a future release of jdbbackup-core...
		final SourceManager sourceManager = backupEngine.getSourceManagers().get(new Destination(task.getSource()).getScheme());
		final Function<String, CharSequence> srcManager = sourceManager.getExtensionBuilder();
		task.getDestinations().forEach(d -> this.checkDestination(d, srcManager));
	}

	private void checkDestination(String destination, Function<String, CharSequence> extBuilder) {
		final Destination uri = new Destination(destination);
		final DestinationManager<?> destinationManager = backupEngine.getDestinationManagers().get(uri.getScheme());
		destinationManager.validate(uri.getPath(), extBuilder);
	}

	private Configuration getConfiguration(String confFilePath) {
		try {
			return Configuration.read(getTasksFile(confFilePath));
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
		Path file = Paths.get(filePath).toAbsolutePath();
		if (!Files.exists(file)) {
			throw new IOException("File "+file+" does not exists");
		} else if (!Files.isRegularFile(file)) {
			throw new IOException("File "+file+" is not a file");
		} else if (!Files.isReadable(file)) {
			throw new IOException("Can't read file "+file);
		}
		return file;
	}
}
