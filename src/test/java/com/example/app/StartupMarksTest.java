package com.example.app;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** PERF-19: the start's phase marks, as the Debug Log writes them and as the measuring script reads them. */
class StartupMarksTest {

    @Test
    void theMarksReadAsALineAndAsAFile() {
        List<StartupMarks.Mark> marks =
                List.of(new StartupMarks.Mark("main", 120), new StartupMarks.Mark("first-paint", 690));
        assertEquals("main 120 ms · first-paint 690 ms", StartupMarks.line(marks));
        assertEquals("main 120\nfirst-paint 690\n", StartupMarks.file(marks));
        StartupMarks.mark("test");
        assertTrue(StartupMarks.marks().stream().anyMatch(m -> m.phase().equals("test") && m.millis() >= 0));
    }
}
