package com.summay.studybuddy;

import java.util.*;

public class Deck {
    public String name;
    public List<Flashcard> cards = new ArrayList<>();

    public Deck() {}
    public Deck(String name) { this.name = name; }
}
