package com.summay.studybuddy;

import java.time.*;
import java.util.*;

public class Scheduler {
    // Simplified SM-2 style scheduler
    public void grade(Flashcard c, int quality) {
        quality = Math.max(0, Math.min(5, quality));
        if (quality < 3) {
            c.repetitions = 0;
            c.intervalDays = 1;
        } else {
            if (c.repetitions == 0) c.intervalDays = 1;
            else if (c.repetitions == 1) c.intervalDays = 6;
            else c.intervalDays = (int)Math.round(c.intervalDays * c.ease);
            c.repetitions += 1;
        }
        c.ease = Math.max(1.3, c.ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02)));
        c.setDue(LocalDate.now().plusDays(c.intervalDays));
    }

    public List<Flashcard> todaysQueue(Decks decks) {
        LocalDate today = LocalDate.now();
        List<Flashcard> due = new ArrayList<>();
        for (Deck d : decks.decks) {
            for (Flashcard c : d.cards) {
                if (!c.dueDate().isAfter(today)) due.add(c);
            }
        }
        return due;
    }
}
