package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
		log.info("Loading tasks file from {}", path);
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
		final Scheduler scheduler = new Scheduler();
		for (final Task task : tasks) {
			scheduler.schedule(task.getSchedule(), new BackupTask(backupEngine, task));
			log.info("{} is scheduled with {} schedule", task.getName(), task.getSchedule());
		}
		scheduler.start();
	}
	
	private static class BackupTask implements Runnable {
		private final JDbBackup engine;
		private final Task task;
		private final AtomicBoolean running;
		
		public BackupTask(JDbBackup engine, Task task) {
			this.engine = engine;
			this.task = task;
			this.running = new AtomicBoolean();
		}

		@Override
		public void run() {
			if (running.compareAndSet(false, true)) {
				try {
					log.info("Starting {} task...", task.getName());
					engine.backup(task.getSource(), task.getDestinations().toArray(String[]::new));
					log.info("{} task succeeded", task.getName());
				} catch (Throwable e) {
					log.error(task.getName()+" task failed", e);
				}
				running.set(false);
			} else {
				log.warn("{} task skipped because it is already running", task.getName());
			}
		}
	}
}
