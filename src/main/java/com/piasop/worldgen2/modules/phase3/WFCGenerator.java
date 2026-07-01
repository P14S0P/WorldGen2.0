package com.piasop.worldgen2.modules.phase3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Small deterministic 2D WFC solver for structure footprints.
 */
public final class WFCGenerator {
    private final int size;
    private final List<String> modules;
    private final Map<String, Set<String>> adjacency;
    private final Random random;

    public WFCGenerator(int size, List<String> modules, Map<String, Set<String>> adjacency, long seed) {
        this.size = size;
        this.modules = modules;
        this.adjacency = adjacency;
        this.random = new Random(seed);
    }

    public String[] generate(String fallback) {
        @SuppressWarnings("unchecked")
        Set<String>[][] possibilities = new Set[size][size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                possibilities[z][x] = new HashSet<>(modules);
            }
        }

        while (true) {
            Cell cell = lowestEntropyCell(possibilities);
            if (cell == null) {
                break;
            }
            if (cell.options().isEmpty()) {
                return fallbackGrid(fallback);
            }

            String chosen = choose(cell.options());
            possibilities[cell.z()][cell.x()] = new HashSet<>(Set.of(chosen));
            if (!propagate(possibilities, cell.x(), cell.z())) {
                return fallbackGrid(fallback);
            }
        }

        String[] grid = new String[size * size];
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                grid[(z * size) + x] = possibilities[z][x].iterator().next();
            }
        }
        return grid;
    }

    private Cell lowestEntropyCell(Set<String>[][] possibilities) {
        List<Cell> candidates = new ArrayList<>();
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int entropy = possibilities[z][x].size();
                if (entropy > 1) {
                    candidates.add(new Cell(x, z, possibilities[z][x]));
                }
            }
        }
        return candidates.stream().min(Comparator.comparingInt(c -> c.options().size())).orElse(null);
    }

    private boolean propagate(Set<String>[][] possibilities, int startX, int startZ) {
        ArrayList<int[]> queue = new ArrayList<>();
        queue.add(new int[]{startX, startZ});
        int[] dx = {-1, 1, 0, 0};
        int[] dz = {0, 0, -1, 1};

        while (!queue.isEmpty()) {
            int[] current = queue.remove(queue.size() - 1);
            int x = current[0];
            int z = current[1];

            for (int i = 0; i < dx.length; i++) {
                int nx = x + dx[i];
                int nz = z + dz[i];
                if (nx < 0 || nz < 0 || nx >= size || nz >= size) {
                    continue;
                }

                Set<String> allowed = new HashSet<>();
                for (String module : possibilities[z][x]) {
                    allowed.addAll(adjacency.getOrDefault(module, Set.of(module)));
                }

                Set<String> neighbor = possibilities[nz][nx];
                if (!neighbor.retainAll(allowed)) {
                    continue;
                }
                if (neighbor.isEmpty()) {
                    return false;
                }
                queue.add(new int[]{nx, nz});
            }
        }
        return true;
    }

    private String choose(Set<String> options) {
        int index = random.nextInt(options.size());
        int cursor = 0;
        for (String option : options) {
            if (cursor++ == index) {
                return option;
            }
        }
        return options.iterator().next();
    }

    private String[] fallbackGrid(String fallback) {
        String[] grid = new String[size * size];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = fallback;
        }
        grid[(size / 2 * size) + (size / 2)] = "core";
        return grid;
    }

    private record Cell(int x, int z, Set<String> options) {
    }
}