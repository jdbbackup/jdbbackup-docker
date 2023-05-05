package com.fathzer.jdbbackup.cron;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fathzer.jdbbackup.cron.parameters.Task;

class ConfigurationTest {
	
	@Test
	void test() throws IOException {
		String json = "{'proxy':'host:3128','tasks':[{'name':'name','schedule':'@daily','source':'fake://xxx','destinations':['file://backup']}]}".replace('\'', '"');
		Configuration conf = Configuration.read(new ByteArrayInputStream(json.getBytes()));
		assertNotNull(conf.getProxy());
		assertNull(conf.getProxy().getBase64Login());
		assertEquals("host",conf.getProxy().getHost());
		assertEquals(1, conf.getTasks().size());
		final Task task = conf.getTasks().get(0);
		assertEquals("name", task.getName());
		assertEquals("0 0 * * *", task.getSchedule());
		assertEquals("fake://xxx", task.getSource());
		assertEquals(1, task.getDestinations().size());
		assertEquals("file://backup", task.getDestinations().get(0));

		illegal("");
		illegal("{}");
		
		illegal("{'proxy':'wrong','tasks':[{'name':'name','schedule':'@daily','source':'fake:\\xxx','destinations':['file://backup']}]}");
	}
	
	private InputStream illegal(String content) throws IOException {
		content = content.replace('\'', '"');
		try (InputStream stream=new ByteArrayInputStream(content.getBytes())) {
			assertThrows(IllegalArgumentException.class, () -> Configuration.read(stream));
		}
		return null;
	}

}
