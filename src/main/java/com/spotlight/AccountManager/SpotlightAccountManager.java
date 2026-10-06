package com.spotlight.AccountManager;

import com.spotlight.LootItem;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;

import java.io.*;
import java.nio.Buffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.time.Instant;
import java.util.ArrayList;

@Slf4j
public class SpotlightAccountManager {

    private final File directory;

    private final int tooOldAge = 5000;

    public SpotlightAccountManager(File spotlightDirectory) {
        directory = spotlightDirectory;
        verifySpotlightDirectory();
    }

    public ArrayList<SpotlightAccountInfo> getAllAccountInfo() {
        verifySpotlightDirectory();

        ArrayList<SpotlightAccountInfo> out = new ArrayList<>();
        if(directory.listFiles() == null) {
            return out;
        }
        for(File f : directory.listFiles()) {
            // The folder also holds the .wav sounds; only account files are .txt
            if(!f.getName().endsWith(".txt") || Instant.now().toEpochMilli() - f.lastModified() >= tooOldAge) {
                continue;
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                String line = reader.readLine();
                if(line != null) {
                    out.add(new SpotlightAccountInfo(line));
                }
            } catch(IOException | RuntimeException ex) {
                // Another client may be mid-write; a half-written file used to throw a parse
                // exception out of the per-frame blackout update. Skip it until the next read.
                log.debug("Skipping unreadable account file {}", f.getName(), ex);
            }
        }
        return out;
    }

    //Returns false if unable to save to file
    public boolean saveAccountInfo(String name, int prayer, int health, int backpackSpace, ArrayList<LootItem> groundItems, WorldPoint playerPosition, int profit, int GPhr) {
        verifySpotlightDirectory();

        SpotlightAccountInfo info = new SpotlightAccountInfo(name,health,prayer,backpackSpace,groundItems, playerPosition,profit, GPhr);

        File file = new File(directory,name + ".txt");

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(info.toString());
        } catch(IOException ex) {
            log.warn("Could not save account info to {}", file.getName(), ex);
            return false;
        }
        return true;

    }

    //Returns true if already exists, false if directory created
    public boolean verifySpotlightDirectory() {
        return !directory.mkdir();
    }
}
