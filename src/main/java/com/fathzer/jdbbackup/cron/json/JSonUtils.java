package com.fathzer.jdbbackup.cron.json;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


interface JSonUtils {
	default String getField(JsonNode node, String fieldName) {
		if (node.has(fieldName)) {
			return node.get(fieldName).asText();
		} else {
			throw new IllegalArgumentException(fieldName+" attribute is missing");
		}
	}

	default <T> List<T> getList(JsonNode node, String fieldName, Class<T> aClass) throws IOException {
		node = node.get(fieldName);
		if (node==null) {
			throw new IllegalArgumentException(fieldName+" attribute is missing or is null");
		}
		return new ObjectMapper().readerForListOf(aClass).readValue(node);
	}

}
