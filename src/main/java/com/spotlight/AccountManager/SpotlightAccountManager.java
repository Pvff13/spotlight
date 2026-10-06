package com.spotlight.AccountManager;

import com.spotlight.LootItem;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.util.Filepath;

import java.io.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class SpotlightAccountManager {

    private final Filepath directory;

    private final int tooOldAge = 5000;

    public SpotlightAccountManager(Filepath spotlightDirectory) {
        directory = spotlightDirectory;
        verifySpotlightDirectory();
    }

    public ArrayList<SpotlightAccountInfo> getAllAccountInfo() {
        ArrayList<SpotlightAccountInfo> out = new ArrayList<>();
        if(!directory.isDirectory()) {
            return out;
        }
        List<Filepath> files;
        try (Stream<Filepath> walk = directory.walk(1)) {
            // The folder also holds the .wav sounds; only account files are .txt
            files = walk.filter(f -> f.getFileName().endsWith(".txt")).collect(Collectors.toList());
        } catch(IOException ex) {
            log.debug("Could not list account files", ex);
            return out;
        }
        for(Filepath f : files) {
            try {
                if(Instant.now().toEpochMilli() - f.getLastModifiedTime().toMillis() >= tooOldAge) {
                    continue;
                }
                try (BufferedReader reader = f.openBufferedReader()) {
                    String line = reader.readLine();
                    if(line != null) {
                        out.add(new SpotlightAccountInfo(line));
                    }
                }
            } catch(IOException | RuntimeException ex) {
                // Another client may be mid-write; a half-written file used to throw a parse
                // exception out of the per-frame blackout update. Skip it until the next read.
                log.debug("Skipping unreadable account file {}", f.getFileName(), ex);
            }
        }
        return out;
    }

    //Returns false if unable to save to file
    public boolean saveAccountInfo(String name, int prayer, int health, int backpackSpace, ArrayList<LootItem> groundItems, WorldPoint playerPosition, int profit, int GPhr) {
        verifySpotlightDirectory();

        SpotlightAccountInfo info = new SpotlightAccountInfo(name,health,prayer,backpackSpace,groundItems, playerPosition,profit, GPhr);

        try {
            // joinSegment rejects names that aren't valid file names (e.g. "?" before login)
            directory.joinSegment(name + ".txt").write(info.toString());
        } catch(IOException | IllegalArgumentException ex) {
            log.debug("Could not save account info for {}", name, ex);
            return false;
        }
        return true;

    }

    //Returns true if the directory exists
    public boolean verifySpotlightDirectory() {
        try {
            directory.createDirectories();
            return true;
        } catch(IOException ex) {
            log.warn("Could not create Spotlight data directory", ex);
            return false;
        }
    }
}
