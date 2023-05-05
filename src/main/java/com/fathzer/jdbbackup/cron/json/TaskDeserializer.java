package com.fathzer.jdbbackup.cron.json;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fathzer.jdbbackup.cron.parameters.Task;

public class TaskDeserializer extends StdDeserializer<Task> {
	private static final long serialVersionUID = 1L;

	public TaskDeserializer() {
		super(Task.class);
	}

	@Override
	public Task deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
		JsonNode node = jp.getCodec().readTree(jp);
		return new Task(getField(node, "name"), getField(node, "source"), getList(node, "destinations"), getField(node, "schedule"));
	}
	
	private String getField(JsonNode node, String fieldName) {
		if (node.has(fieldName)) {
			return node.get(fieldName).asText();
		} else {
			throw new IllegalArgumentException(fieldName+" attribute is missing");
		}
	}

	private List<String> getList(JsonNode node, String fieldName) throws IOException {
		node = node.get(fieldName);
		if (node==null) {
			throw new IllegalArgumentException(fieldName+" attribute is missing or is null");
		}
		return new ObjectMapper().readerForListOf(String.class).readValue(node);
	}
}
