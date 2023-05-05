package com.fathzer.jdbbackup.cron.json;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fathzer.jdbbackup.cron.parameters.Task;

public class TaskDeserializer extends StdDeserializer<Task> implements JSonUtils{
	private static final long serialVersionUID = 1L;

	public TaskDeserializer() {
		super(Task.class);
	}

	@Override
	public Task deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
		JsonNode node = jp.getCodec().readTree(jp);
		return new Task(getField(node, "name"), getField(node, "source"), getList(node, "destinations", String.class), getField(node, "schedule"));
	}
}
