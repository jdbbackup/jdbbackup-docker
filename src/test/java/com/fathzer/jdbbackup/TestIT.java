package com.fathzer.jdbbackup;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import org.junit.jupiter.api.Test;

class JarIT {

	@Test
	void test() throws IOException {
		// Verify that the META-INF services files are safely merged
		try (JarFile jar = new JarFile(System.getProperty("jarFile"))) {
			assertEquals(getExpectedServices(SourceManager.class), getDelaredServices(jar, SourceManager.class),getMessage(SourceManager.class, "Source managers"));
			assertEquals(getExpectedServices(DestinationManager.class), getDelaredServices(jar, DestinationManager.class),getMessage(DestinationManager.class, "Destination managers"));
		}
	}
	
	private String getMessage(Class<?> serviceClass, String wording) {
		return wording+" declaration is invalid in final jar ("+getEntryName(serviceClass)+" does not contains the expected service list)";
	}

	private Set<String> getDelaredServices(JarFile jar, Class<?> serviceClass) throws IOException {
		final ZipEntry entry = jar.getEntry(getEntryName(serviceClass));
		assertNotNull(entry);
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
			return reader.lines().map(this::uncomment).filter(l -> !l.isBlank()).collect(Collectors.toSet());
		}
	}
	
	private String uncomment(String ln) {
        final int ci = ln.indexOf('#');
        if (ci >= 0) {
        	ln = ln.substring(0, ci);
        }
        ln = ln.trim();
		return ln;
	}

	private String getEntryName(Class<?> serviceClass) {
		return "META-INF/services/"+serviceClass.getName();
	}
	
	private Set<String> getExpectedServices(Class<?> serviceClass) {
		return ServiceLoader.load(serviceClass).stream().map(p -> p.get().getClass().getName()).collect(Collectors.toSet());
	}
}
