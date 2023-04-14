package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.parameters.Parameters;
import com.fathzer.plugin.loader.utils.ProxySettings;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.Scheduler;
import it.sauronsoftware.cron4j.SchedulingPattern;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(value = AccessLevel.PACKAGE)
class Configuration {
	private ProxySettings proxy;
	private List<Parameters.Task> tasks;
	
	Configuration(Path path) throws IOException {
		log.info("Loading tasks file in {}", path);
		final ObjectMapper mapper = new ObjectMapper();
		try (InputStream in = Files.newInputStream(path)) {
			init(mapper.readValue(in, Parameters.class));
		}
	}
	
	private void init(Parameters params) {
		this.proxy = params.getProxy() == null ? null : ProxySettings.fromString(params.getProxy());
		// Verify scheduling patterns are correct and replace shortcuts
		this.tasks = params.getTasks().stream().map(t -> new Parameters.Task(t.getName(), t.getSource(), t.getDestinations(), toCron4JSchedule(t.getSchedule()))).collect(Collectors.toList());
	}
	
	private static String toCron4JSchedule(String schedule) {
		if ("@yearly".equals(schedule) || "@annually".equals(schedule)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "0 0 1 1 *"; //$NON-NLS-1$
		} else if ("@monthly".equals(schedule)) { //$NON-NLS-1$
			return "0 0 1 * *"; //$NON-NLS-1$
		} else if ("@weekly".equals(schedule)) { //$NON-NLS-1$
			return "0 0 * * 0"; //$NON-NLS-1$
		} else if ("@daily".equals(schedule) || "@midnight".equals(schedule)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "0 0 * * *"; //$NON-NLS-1$
		} else if ("@hourly".equals(schedule)) { //$NON-NLS-1$
			return "0 * * * *"; //$NON-NLS-1$
		} else {
			// Verify the pattern is ok
			try {
				new SchedulingPattern(schedule);
			} catch (InvalidPatternException e) {
				throw new IllegalArgumentException(e);
			}
			return schedule;
		}
	}
	
	void schedule(JDbBackup backupEngine) {
		for (final Parameters.Task task : tasks) {
			final Scheduler scheduler = new Scheduler();
			final Runnable scheduledTask = () -> {
				try {
					backupEngine.backup(proxy, task.getSource(), task.getDestinations().toArray(String[]::new));
					log.info("{} task succeeded", task.getName());
				} catch (Throwable e) {
					log.error(task.getName()+" task failed", e);
				}
			};
			scheduler.schedule(toCron4JSchedule(task.getSchedule()), scheduledTask);
			scheduler.start();
			log.info("{} is scheduled with {} schedule", task.getName(), task.getSchedule());
		}
	}
}
