package com.fathzer.jdbbackup.cron.parameters;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.sauronsoftware.cron4j.InvalidPatternException;
import it.sauronsoftware.cron4j.SchedulingPattern;
import lombok.Getter;

/** A backup task.
 */
@Getter
public class Task {
	private String name;
	private String source;
	private List<String> destinations;
	private String schedule;

	/** Constructor.
	 * <br>All arguments should not be null or empty
	 * @param name The task's name.
	 * @param source The backup data source
	 * @param destinations The backup destinations
	 * @param schedule The schedule in cron format or one of the supported abbreviated (@yearly, @annually, @monthly, @weekly, @daily, @midnight, @hourly)
	 */
	@JsonCreator
	public Task(@JsonProperty("name") String name, @JsonProperty("source") String source, @JsonProperty("destinations") List<String> destinations, @JsonProperty("schedule") String schedule) {
		// Verify arguments
		validate(name, "Name");
		validate(source, "Source");
		validate(destinations, "Destinations");
		destinations.forEach(d -> validate(d, "Values in destinations"));
		validate(schedule, "Schedule");
		// Build the object
		this.name = name;
		this.source = source;
		this.destinations = destinations;
		this.schedule = getCron4JSchedule(schedule);
	}

	static String getCron4JSchedule(String schedule) {
		if ("@yearly".equals(schedule) || "@annually".equals(schedule)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "0 0 1 1 *"; //$NON-NLS-1$
		}
		if ("@monthly".equals(schedule)) { //$NON-NLS-1$
			return "0 0 1 * *"; //$NON-NLS-1$
		}
		if ("@weekly".equals(schedule)) { //$NON-NLS-1$
			return "0 0 * * 0"; //$NON-NLS-1$
		}
		if ("@daily".equals(schedule) || "@midnight".equals(schedule)) { //$NON-NLS-1$ //$NON-NLS-2$
			return "0 0 * * *"; //$NON-NLS-1$
		}
		if ("@hourly".equals(schedule)) { //$NON-NLS-1$
			return "0 * * * *"; //$NON-NLS-1$
		} else {
			// Verify the pattern is ok
			try {
				new SchedulingPattern(schedule);
			} catch (InvalidPatternException e) {
				throw new IllegalArgumentException(e);
			}
			return schedule;
		}
	}
	
	private static void validate(String content, String wording) {
		if (content==null || content.isEmpty()) {
			throw new IllegalArgumentException(wording+" should not be null or empty");
		}
	}

	private static <T> void validate(List<T> list, String wording) {
		if (list==null || list.isEmpty()) {
			throw new IllegalArgumentException(wording+" should not be null or empty");
		}
	}

}