package com.fathzer.jdbbackup.cron;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fathzer.jdbbackup.JDbBackup;
import com.fathzer.jdbbackup.cron.json.TaskDeserializer;
import com.fathzer.jdbbackup.cron.parameters.Parameters;
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
	
	static {
		SimpleModule module = new SimpleModule();
		module.addDeserializer(Task.class, new TaskDeserializer());
		MAPPER.registerModule(module);
	}
	
	private ProxySettings proxy;
	private List<Task> tasks;
	
	Configuration(Path path) throws IOException {
		log.info("Loading tasks file in {}", path);
		try (InputStream in = Files.newInputStream(path)) {
			init(MAPPER.readValue(in, Parameters.class));
		}
	}
	
	Configuration(InputStream in) throws IOException {
		try {
			init(MAPPER.readValue(in, Parameters.class));
		} catch (DatabindException e) {
			throw new IllegalArgumentException(e);
		}
	}
	
	private void init(Parameters params) {
		this.proxy = params.getProxy() == null ? null : ProxySettings.fromString(params.getProxy());
		this.tasks = params.getTasks();
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
