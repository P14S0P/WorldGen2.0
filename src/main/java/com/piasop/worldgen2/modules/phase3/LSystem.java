package com.piasop.worldgen2.modules.phase3;

import java.util.Map;

/**
 * Lightweight deterministic L-system expander with a hard symbol cap.
 */
public record LSystem(String axiom, Map<Character, String> rules, int iterations, int maxSymbols) {
    public String generate() {
        String current = axiom;
        for (int i = 0; i < iterations; i++) {
            StringBuilder next = new StringBuilder(Math.min(maxSymbols, current.length() * 2));
            for (int c = 0; c < current.length() && next.length() < maxSymbols; c++) {
                char symbol = current.charAt(c);
                String replacement = rules.get(symbol);
                if (replacement == null) {
                    next.append(symbol);
                } else if (next.length() + replacement.length() <= maxSymbols) {
                    next.append(replacement);
                } else {
                    int remaining = maxSymbols - next.length();
                    next.append(replacement, 0, Math.max(0, remaining));
                }
            }
            current = next.toString();
            if (current.length() >= maxSymbols) {
                return current;
            }
        }
        return current;
    }
}