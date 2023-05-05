package com.fathzer.jdbbackup.cron.parameters;

import java.util.List;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Parameters {
	private String proxy;
	private List<Task> tasks;
}
