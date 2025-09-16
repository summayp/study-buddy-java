package com.summay.studybuddy;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;

public class SchedulerTest {
    @Test
    public void testGradeIncreasesIntervalOnGoodAnswer() {
        Flashcard c = Flashcard.newCard("q","a");
        Scheduler s = new Scheduler();
        s.grade(c, 5);
        assertTrue(c.intervalDays >= 1);
        assertTrue(c.dueDate().isAfter(LocalDate.now().minusDays(1)));
    }

    @Test
    public void testResetOnFail() {
        Flashcard c = Flashcard.newCard("q","a");
        Scheduler s = new Scheduler();
        s.grade(c, 5);
        s.grade(c, 2);
        assertEquals(1, c.intervalDays);
        assertEquals(0, c.repetitions);
    }
}
