package com.piasop.worldgen2.modules.phase1;

/**
 * Deterministic 2D OpenSimplex2S-style gradient noise.
 */
final class OpenSimplex2S {
    private static final int PSIZE = 256;
    private static final int PMASK = 255;
    private static final double STRETCH_2D = -0.211324865405187;
    private static final double SQUISH_2D = 0.366025403784439;
    private static final double NORM_2D = 47.0;

    private static final Grad2[] GRADIENTS_2D = {
            new Grad2(5, 2), new Grad2(2, 5), new Grad2(-5, 2), new Grad2(-2, 5),
            new Grad2(5, -2), new Grad2(2, -5), new Grad2(-5, -2), new Grad2(-2, -5),
            new Grad2(4, 3), new Grad2(3, 4), new Grad2(-4, 3), new Grad2(-3, 4),
            new Grad2(4, -3), new Grad2(3, -4), new Grad2(-4, -3), new Grad2(-3, -4)
    };

    private final short[] perm;

    OpenSimplex2S(long seed) {
        perm = new short[PSIZE];
        short[] source = new short[PSIZE];
        for (short i = 0; i < PSIZE; i++) {
            source[i] = i;
        }

        long state = seed;
        for (int i = PSIZE - 1; i >= 0; i--) {
            state = (state * 6364136223846793005L) + 1442695040888963407L;
            int r = (int) ((state + 31) % (i + 1));
            if (r < 0) {
                r += i + 1;
            }
            perm[i] = source[r];
            source[r] = source[i];
        }
    }

    double eval(double x, double y) {
        double stretchOffset = (x + y) * STRETCH_2D;
        double xs = x + stretchOffset;
        double ys = y + stretchOffset;

        int xsb = fastFloor(xs);
        int ysb = fastFloor(ys);

        double squishOffset = (xsb + ysb) * SQUISH_2D;
        double dx0 = x - (xsb + squishOffset);
        double dy0 = y - (ysb + squishOffset);

        double xins = xs - xsb;
        double yins = ys - ysb;
        double inSum = xins + yins;

        double value = 0.0;

        double dx1 = dx0 - 1.0 - SQUISH_2D;
        double dy1 = dy0 - SQUISH_2D;
        double attn1 = 2.0 - dx1 * dx1 - dy1 * dy1;
        if (attn1 > 0) {
            attn1 *= attn1;
            value += attn1 * attn1 * extrapolate(xsb + 1, ysb, dx1, dy1);
        }

        double dx2 = dx0 - SQUISH_2D;
        double dy2 = dy0 - 1.0 - SQUISH_2D;
        double attn2 = 2.0 - dx2 * dx2 - dy2 * dy2;
        if (attn2 > 0) {
            attn2 *= attn2;
            value += attn2 * attn2 * extrapolate(xsb, ysb + 1, dx2, dy2);
        }

        if (inSum <= 1.0) {
            double zins = 1.0 - inSum;
            if (zins > xins || zins > yins) {
                if (xins > yins) {
                    double dx3 = dx0 - 1.0 - (2.0 * SQUISH_2D);
                    double dy3 = dy0 - (2.0 * SQUISH_2D);
                    double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                    if (attn3 > 0) {
                        attn3 *= attn3;
                        value += attn3 * attn3 * extrapolate(xsb + 1, ysb - 1, dx3, dy3);
                    }
                } else {
                    double dx3 = dx0 - (2.0 * SQUISH_2D);
                    double dy3 = dy0 - 1.0 - (2.0 * SQUISH_2D);
                    double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                    if (attn3 > 0) {
                        attn3 *= attn3;
                        value += attn3 * attn3 * extrapolate(xsb - 1, ysb + 1, dx3, dy3);
                    }
                }
            } else {
                double dx3 = dx0 - 1.0 - (2.0 * SQUISH_2D);
                double dy3 = dy0 - 1.0 - (2.0 * SQUISH_2D);
                double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                if (attn3 > 0) {
                    attn3 *= attn3;
                    value += attn3 * attn3 * extrapolate(xsb + 1, ysb + 1, dx3, dy3);
                }
            }
        } else {
            double zins = 2.0 - inSum;
            if (zins < xins || zins < yins) {
                if (xins > yins) {
                    double dx3 = dx0 - 2.0 - (2.0 * SQUISH_2D);
                    double dy3 = dy0 - (2.0 * SQUISH_2D);
                    double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                    if (attn3 > 0) {
                        attn3 *= attn3;
                        value += attn3 * attn3 * extrapolate(xsb + 2, ysb, dx3, dy3);
                    }
                } else {
                    double dx3 = dx0 - (2.0 * SQUISH_2D);
                    double dy3 = dy0 - 2.0 - (2.0 * SQUISH_2D);
                    double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                    if (attn3 > 0) {
                        attn3 *= attn3;
                        value += attn3 * attn3 * extrapolate(xsb, ysb + 2, dx3, dy3);
                    }
                }
            } else {
                double dx3 = dx0 - 1.0 - (2.0 * SQUISH_2D);
                double dy3 = dy0 - 1.0 - (2.0 * SQUISH_2D);
                double attn3 = 2.0 - dx3 * dx3 - dy3 * dy3;
                if (attn3 > 0) {
                    attn3 *= attn3;
                    value += attn3 * attn3 * extrapolate(xsb + 1, ysb + 1, dx3, dy3);
                }
            }

            double dx4 = dx0 - 1.0 - (3.0 * SQUISH_2D);
            double dy4 = dy0 - 1.0 - (3.0 * SQUISH_2D);
            double attn4 = 2.0 - dx4 * dx4 - dy4 * dy4;
            if (attn4 > 0) {
                attn4 *= attn4;
                value += attn4 * attn4 * extrapolate(xsb + 1, ysb + 1, dx4, dy4);
            }
        }

        return value / NORM_2D;
    }

    private double extrapolate(int xsb, int ysb, double dx, double dy) {
        int idx = perm[(perm[xsb & PMASK] + ysb) & PMASK] & (GRADIENTS_2D.length - 1);
        Grad2 g = GRADIENTS_2D[idx];
        return (g.dx * dx) + (g.dy * dy);
    }

    private static int fastFloor(double x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }

    private record Grad2(double dx, double dy) {
    }
}
