package com.summay.studybuddy;

import java.util.*;

public class Decks {
    public List<Deck> decks = new ArrayList<>();

    public Deck getDeck(String name) {
        for (Deck d : decks.decks) if (d.name.equalsIgnoreCase(name)) return d;
        return null;
    }
}
