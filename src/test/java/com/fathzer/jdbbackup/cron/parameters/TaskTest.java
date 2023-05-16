package com.fathzer.jdbbackup.cron.parameters;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TaskTest {

	@Test
	void test() {
		assertEquals("0 * * * *",Task.getCron4JSchedule("@hourly"));
		assertEquals("0 0 * * *",Task.getCron4JSchedule("@daily"));
		assertEquals("0 0 * * *",Task.getCron4JSchedule("@midnight"));
		assertEquals("0 0 * * 0",Task.getCron4JSchedule("@weekly"));
		assertEquals("0 0 1 * *",Task.getCron4JSchedule("@monthly"));
		assertEquals("0 0 1 1 *", Task.getCron4JSchedule("@yearly"));
		assertEquals("0 0 1 1 *", Task.getCron4JSchedule("@annually"));
		// Test with correct pattern
		assertEquals("30 1 1,15 * *", Task.getCron4JSchedule("30 1 1,15 * *"));
		// Test with wrong pattern
		assertThrows(IllegalArgumentException.class, () -> Task.getCron4JSchedule("0 0"));
	}

}
