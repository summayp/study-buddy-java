package com.summay.studybuddy;

import java.io.*;
import java.nio.file.*;
import com.google.gson.*;

public class Storage {
    private final Path path;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public Storage(Path path) throws Exception {
        this.path = path;
        if (!Files.exists(path.getParent())) Files.createDirectories(path.getParent());
        if (!Files.exists(path)) save(new Decks());
    }

    public Decks load() throws Exception {
        try (Reader r = Files.newBufferedReader(path)) {
            Decks decks = gson.fromJson(r, Decks.class);
            if (decks == null) decks = new Decks();
            return decks;
        }
    }

    public void save(Decks decks) throws Exception {
        try (Writer w = Files.newBufferedWriter(path)) {
            gson.toJson(decks, w);
        }
    }
}
