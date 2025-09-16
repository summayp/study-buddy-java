package com.summay.studybuddy;

import java.nio.file.*;
import java.util.*;

public class Cli {
    private final Storage storage;
    private final Scheduler scheduler;

    public Cli() throws Exception {
        Path path = Paths.get(System.getProperty("user.home"), ".studybuddy", "decks.json");
        this.storage = new Storage(path);
        this.scheduler = new Scheduler();
    }

    public void run(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--help")) {
            printHelp();
            return;
        }
        String cmd = args[0];
        switch (cmd) {
            case "add-deck" -> addDeck(args);
            case "add-card" -> addCard(args);
            case "list" -> list();
            case "review" -> review();
            default -> printHelp();
        }
    }

    private void printHelp() {
        System.out.println("Study Buddy CLI");
        System.out.println("Commands:");
        System.out.println("  add-deck <name>");
        System.out.println("  add-card <deck> <front> <back>");
        System.out.println("  list");
        System.out.println("  review");
    }

    private void addDeck(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: add-deck <name>");
            return;
        }
        String name = args[1];
        Decks decks = storage.load();
        if (decks.getDeck(name) != null) {
            System.out.println("Deck already exists.");
            return;
        }
        decks.decks.add(new Deck(name));
        storage.save(decks);
        System.out.println("Created deck: " + name);
    }

    private void addCard(String[] args) throws Exception {
        if (args.length < 4) {
            System.err.println("Usage: add-card <deck> <front> <back>");
            return;
        }
        String deckName = args[1];
        String front = args[2];
        String back = args[3];
        Decks decks = storage.load();
        Deck deck = decks.getDeck(deckName);
        if (deck == null) {
            System.err.println("Deck not found: " + deckName);
            return;
        }
        deck.cards.add(Flashcard.newCard(front, back));
        storage.save(decks);
        System.out.println("Added card to " + deckName);
    }

    private void list() throws Exception {
        Decks decks = storage.load();
        for (Deck d : decks.decks) {
            System.out.println("- " + d.name + " (" + d.cards.size() + " cards)");
        }
    }

    private void review() throws Exception {
        Decks decks = storage.load();
        List<Flashcard> session = scheduler.todaysQueue(decks);
        if (session.isEmpty()) {
            System.out.println("No cards due today. 🎉");
            return;
        }
        try (Scanner sc = new Scanner(System.in)) {
            for (Flashcard c : session) {
                System.out.println("\nFront: " + c.front);
                System.out.print("Show answer? (enter)");
                sc.nextLine();
                System.out.println("Back: " + c.back);
                System.out.print("Grade (0-5): ");
                int grade = Integer.parseInt(sc.nextLine().trim());
                scheduler.grade(c, grade);
            }
        }
        storage.save(decks);
        System.out.println("\nSession complete. Progress saved.");
    }
}
