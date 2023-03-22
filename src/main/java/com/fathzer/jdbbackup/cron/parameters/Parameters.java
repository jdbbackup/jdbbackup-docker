package com.fathzer.jdbbackup.cron.parameters;

import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class Parameters {
	@Getter
	@AllArgsConstructor
	@NoArgsConstructor(access = AccessLevel.PRIVATE)
	public static class Task {
		private String name;
		private String source;
		private List<String> destinations;
		private String schedule;
	}
	
	private String proxy;
	private List<Task> tasks;
}
