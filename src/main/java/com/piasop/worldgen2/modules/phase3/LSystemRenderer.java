package com.piasop.worldgen2.modules.phase3;

import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Converts expanded L-system strings to discrete log and leaf voxels.
 */
public final class LSystemRenderer {
    private static final int MAX_LOGS = 384;
    private static final int MAX_LEAVES = 768;

    public RenderedTree render(String pattern, TreeModule.TreePrototype prototype, double windTiltDegrees) {
        Set<Voxel> logs = new LinkedHashSet<>();
        Set<Voxel> leaves = new LinkedHashSet<>();
        ArrayDeque<TurtleState> stack = new ArrayDeque<>();

        TurtleState turtle = TurtleState.root(windTiltDegrees);
        double angle = prototype.branchAngleDegrees();
        int leafRadius = Math.max(1, prototype.maxCanopyRadius() - 1);

        for (int i = 0; i < pattern.length(); i++) {
            char symbol = pattern.charAt(i);
            switch (symbol) {
                case 'F' -> {
                    logs.add(new Voxel((int) Math.round(turtle.x), (int) Math.round(turtle.y), (int) Math.round(turtle.z)));
                    if (logs.size() >= MAX_LOGS) {
                        return new RenderedTree(logs, leaves);
                    }
                    turtle.advance(1.0);
                }
                case 'L' -> addLeafCluster(leaves, turtle, leafRadius);
                case '+' -> turtle.rotateYaw(angle);
                case '-' -> turtle.rotateYaw(-angle);
                case '&' -> turtle.rotatePitch(angle);
                case '^' -> turtle.rotatePitch(-angle);
                case '/' -> turtle.rotateRoll(angle);
                case '\\' -> turtle.rotateRoll(-angle);
                case '[' -> stack.push(turtle.copy());
                case ']' -> {
                    addLeafCluster(leaves, turtle, leafRadius);
                    if (!stack.isEmpty()) {
                        turtle = stack.pop();
                    }
                }
                default -> {
                }
            }
            if (leaves.size() >= MAX_LEAVES) {
                return new RenderedTree(logs, leaves);
            }
        }

        addLeafCluster(leaves, turtle, leafRadius);
        return new RenderedTree(logs, leaves);
    }

    private static void addLeafCluster(Set<Voxel> leaves, TurtleState turtle, int radius) {
        int cx = (int) Math.round(turtle.x);
        int cy = (int) Math.round(turtle.y);
        int cz = (int) Math.round(turtle.z);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    int distance = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                    if (distance > (radius * 2)) {
                        continue;
                    }
                    leaves.add(new Voxel(cx + dx, cy + dy, cz + dz));
                    if (leaves.size() >= MAX_LEAVES) {
                        return;
                    }
                }
            }
        }
    }

    public record Voxel(int x, int y, int z) {
    }

    public record RenderedTree(Set<Voxel> logs, Set<Voxel> leaves) {
    }

    private static final class TurtleState {
        private double x;
        private double y;
        private double z;
        private double yaw;
        private double pitch;
        private double roll;

        private TurtleState(double x, double y, double z, double yaw, double pitch, double roll) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
        }

        static TurtleState root(double windTiltDegrees) {
            return new TurtleState(0.0, 0.0, 0.0, 90.0, windTiltDegrees, 0.0);
        }

        TurtleState copy() {
            return new TurtleState(x, y, z, yaw, pitch, roll);
        }

        void advance(double step) {
            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);
            x += Math.cos(yawRad) * Math.cos(pitchRad) * step;
            y += Math.sin(pitchRad) * step;
            z += Math.sin(yawRad) * Math.cos(pitchRad) * step;
        }

        void rotateYaw(double degrees) {
            yaw += degrees;
        }

        void rotatePitch(double degrees) {
            pitch = clamp(pitch + degrees, -85.0, 85.0);
        }

        void rotateRoll(double degrees) {
            roll += degrees;
            yaw += Math.sin(Math.toRadians(roll)) * 0.25;
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}