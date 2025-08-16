package org.example.profile;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

/**
 * HeightProfileStore persists per-label body height samples and provides simple
 * computations like the best match by height.
 */
public class HeightProfileStore {

    private final Map<Integer, List<Integer>> labelToHeights = new HashMap<>();
    private final String storageFilePath;

    public HeightProfileStore(String storageFilePath) {
        this.storageFilePath = storageFilePath;
    }

    public synchronized void addSample(int label, int bodyHeight) {
        labelToHeights.computeIfAbsent(label, key -> new ArrayList<>()).add(bodyHeight);
    }

    public synchronized Optional<Integer> bestMatchByHeight(int observedHeight, int minSamples, double toleranceFraction) {
        double bestDiff = Double.MAX_VALUE;
        Integer bestLabel = null;
        for (Map.Entry<Integer, List<Integer>> e : labelToHeights.entrySet()) {
            List<Integer> heights = e.getValue();
            if (heights.size() < minSamples) continue;
            double avg = heights.stream().mapToInt(Integer::intValue).average().orElse(0);
            double diff = Math.abs(observedHeight - avg) / Math.max(1.0, avg);
            if (diff < toleranceFraction && diff < bestDiff) {
                bestDiff = diff;
                bestLabel = e.getKey();
            }
        }
        return Optional.ofNullable(bestLabel);
    }

    public synchronized void save() {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(storageFilePath)) {
            gson.toJson(labelToHeights, writer);
            System.out.println("Height profiles saved to " + storageFilePath);
        } catch (IOException e) {
            System.err.println("Error saving height profiles: " + e.getMessage());
        }
    }

    public synchronized void load() {
        File file = new File(storageFilePath);
        if (!file.exists()) {
            System.out.println("No existing height profiles file found. Starting fresh.");
            return;
        }
        Gson gson = new Gson();
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<HashMap<Integer, List<Integer>>>() {}.getType();
            Map<Integer, List<Integer>> loaded = gson.fromJson(reader, type);
            if (loaded != null) {
                labelToHeights.clear();
                labelToHeights.putAll(loaded);
                System.out.println("Height profiles loaded successfully from " + storageFilePath);
            }
        } catch (IOException e) {
            System.err.println("Error loading height profiles: " + e.getMessage());
        }
    }
}


