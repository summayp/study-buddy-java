package com.summay.studybuddy;

import java.time.*;

public class Flashcard {
    public String front;
    public String back;
    public int intervalDays;
    public double ease; // easiness factor
    public int repetitions;
    public String due; // ISO date

    public static Flashcard newCard(String front, String back) {
        Flashcard c = new Flashcard();
        c.front = front;
        c.back = back;
        c.intervalDays = 0;
        c.ease = 2.5;
        c.repetitions = 0;
        c.due = LocalDate.now().toString();
        return c;
    }

    public LocalDate dueDate() {
        return LocalDate.parse(due);
    }

    public void setDue(LocalDate date) {
        this.due = date.toString();
    }
}
