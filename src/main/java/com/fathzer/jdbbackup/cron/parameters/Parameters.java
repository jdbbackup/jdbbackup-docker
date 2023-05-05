package com.fathzer.jdbbackup.cron.parameters;

import java.util.List;

import lombok.Getter;

@Getter
public class Parameters {
	private String proxy;
	private List<Task> tasks;
}
