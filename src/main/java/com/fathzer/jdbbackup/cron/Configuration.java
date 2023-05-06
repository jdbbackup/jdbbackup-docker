package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.parameters.Task;
import com.fathzer.plugin.loader.utils.ProxySettings;

import it.sauronsoftware.cron4j.Scheduler;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter(value = AccessLevel.PACKAGE)
class Configuration {
	private static final ObjectMapper MAPPER = new ObjectMapper();
	
	private ProxySettings proxy;
	private List<Task> tasks;
	
	@JsonCreator
	private Configuration(@JsonProperty("proxy") String proxy, @JsonProperty("tasks") List<Task> tasks) {
		if (tasks==null || tasks.isEmpty()) {
			throw new IllegalArgumentException("Tasks can't be null or empty");
		}
		this.proxy = proxy == null ? null : ProxySettings.fromString(proxy);
		this.tasks = tasks;
	}
	
	static Configuration read(Path path) throws IOException {
		log.info("Loading tasks file in {}", path);
		try (InputStream in = Files.newInputStream(path)) {
			return read(in);
		}
	}
	
	static Configuration read(InputStream in) throws IOException {
		try {
			return MAPPER.readValue(in, Configuration.class);
		} catch (DatabindException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	void schedule(JDbBackup backupEngine) {
		if (proxy!=null) {
			backupEngine.setProxy(proxy.toProxy(), proxy.getLogin());
		}
		for (final Task task : tasks) {
			final Scheduler scheduler = new Scheduler();
			final Runnable scheduledTask = () -> {
				try {
					backupEngine.backup(task.getSource(), task.getDestinations().toArray(String[]::new));
					log.info("{} task succeeded", task.getName());
				} catch (Throwable e) {
					log.error(task.getName()+" task failed", e);
				}
			};
			scheduler.schedule(task.getSchedule(), scheduledTask);
			scheduler.start();
			log.info("{} is scheduled with {} schedule", task.getName(), task.getSchedule());
		}
	}
}
