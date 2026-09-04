package Arkanoid;

import java.util.List;

/**
 * Swept (continuous) collision against the brick lattice.
 *
 * <p>Testing "is the ball overlapping a brick right now" has a subtle failure
 * that is very visible in play: when the ball skims the flat top of a row, the
 * nearest-point test can pick the <em>side</em> face of the neighbouring brick,
 * because at an internal seam both faces are equidistant. The ball then kicks
 * sideways off what is visually a flat surface. Sweeping the ball's path and
 * taking the earliest surface it actually crosses cannot make that mistake:
 * internal seams are never the first thing hit from outside a mass.</p>
 *
 * <p>It also decouples correctness from speed. A discrete test tunnels once the
 * ball moves further per step than a brick is tall, which puts a hard ceiling on
 * how fast relics like {@code OVERTUNE} and {@code ADRENALINE} are allowed to
 * make the ball. Sweeping has no such ceiling.</p>
 *
 * <p>Broadphase is free here: bricks sit on a fixed lattice, so the swept
 * segment's bounding box maps straight onto a range of grid cells.</p>
 */
public final class Physics {

    /** One resolved contact. */
    public static final class Hit {
        public double t;
        public double normalX;
        public double normalY;
        public Bricks brick;

        void set(double t, double nx, double ny, Bricks brick) {
            this.t = t;
            this.normalX = nx;
            this.normalY = ny;
            this.brick = brick;
        }
    }

    /** Brick lookup by lattice cell. Bricks never move, so this is built once. */
    public static final class Grid {

        private final Bricks[] cells = new Bricks[LevelGen.COLS * LevelGen.ROWS];

        public void rebuild(List<Bricks> bricks) {
            java.util.Arrays.fill(cells, null);
            for (Bricks b : bricks) {
                int col = colFor(b.getX() + b.getW() / 2);
                int row = rowFor(b.getY() + b.getH() / 2);
                if (col >= 0 && row >= 0 && col < LevelGen.COLS && row < LevelGen.ROWS) {
                    cells[row * LevelGen.COLS + col] = b;
                }
            }
        }

        public Bricks at(int col, int row) {
            if (col < 0 || row < 0 || col >= LevelGen.COLS || row >= LevelGen.ROWS) {
                return null;
            }
            return cells[row * LevelGen.COLS + col];
        }

        public void remove(Bricks b) {
            int col = colFor(b.getX() + b.getW() / 2);
            int row = rowFor(b.getY() + b.getH() / 2);
            if (col >= 0 && row >= 0 && col < LevelGen.COLS && row < LevelGen.ROWS
                    && cells[row * LevelGen.COLS + col] == b) {
                cells[row * LevelGen.COLS + col] = null;
            }
        }

        public static int colFor(double x) {
            return (int) Math.floor((x - LevelGen.ORIGIN_X) / LevelGen.BRICK_W);
        }

        public static int rowFor(double y) {
            return (int) Math.floor((y - LevelGen.ORIGIN_Y) / LevelGen.BRICK_H);
        }

        /** Neighbours of a brick, for explosion and echo effects. */
        public void neighbours(Bricks of, List<Bricks> out, int radius) {
            int col = colFor(of.getX() + of.getW() / 2);
            int row = rowFor(of.getY() + of.getH() / 2);
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    Bricks b = at(col + dx, row + dy);
                    if (b != null && b.isAlive()) {
                        out.add(b);
                    }
                }
            }
        }
    }

    private Physics() {
    }

    /**
     * Finds the earliest brick the ball's path crosses.
     *
     * @param hit scratch object filled in on success
     * @return true when something was hit within this step
     */
    public static boolean sweep(Grid grid, double px, double py, double dx, double dy,
            double radius, Hit hit) {

        double minX = Math.min(px, px + dx) - radius;
        double maxX = Math.max(px, px + dx) + radius;
        double minY = Math.min(py, py + dy) - radius;
        double maxY = Math.max(py, py + dy) + radius;

        int c0 = Math.max(0, Grid.colFor(minX));
        int c1 = Math.min(LevelGen.COLS - 1, Grid.colFor(maxX));
        int r0 = Math.max(0, Grid.rowFor(minY));
        int r1 = Math.min(LevelGen.ROWS - 1, Grid.rowFor(maxY));

        double bestT = Double.MAX_VALUE;
        double bestNx = 0;
        double bestNy = 0;
        Bricks bestBrick = null;

        double[] out = new double[3];
        for (int row = r0; row <= r1; row++) {
            for (int col = c0; col <= c1; col++) {
                Bricks b = grid.at(col, row);
                if (b == null || !b.isAlive()) {
                    continue;
                }
                if (sweepBox(px, py, dx, dy, radius,
                        b.getX(), b.getY(), b.getX() + b.getW(), b.getY() + b.getH(), out)
                        && out[0] < bestT) {
                    bestT = out[0];
                    bestNx = out[1];
                    bestNy = out[2];
                    bestBrick = b;
                }
            }
        }

        if (bestBrick == null) {
            return false;
        }
        hit.set(bestT, bestNx, bestNy, bestBrick);
        return true;
    }

    /**
     * Swept circle against an axis-aligned box, via the box expanded by the
     * radius and the standard ray/slab test.
     *
     * @param out receives {@code {t, normalX, normalY}}
     * @return true if the segment enters the expanded box within {@code t <= 1}
     */
    public static boolean sweepBox(double px, double py, double dx, double dy, double radius,
            double bMinX, double bMinY, double bMaxX, double bMaxY, double[] out) {

        double minX = bMinX - radius;
        double minY = bMinY - radius;
        double maxX = bMaxX + radius;
        double maxY = bMaxY + radius;

        // Already inside: push out along the shallowest axis so a ball that has
        // somehow ended up embedded still escapes instead of sticking.
        if (px > minX && px < maxX && py > minY && py < maxY) {
            double toLeft = px - minX;
            double toRight = maxX - px;
            double toTop = py - minY;
            double toBottom = maxY - py;
            double m = Math.min(Math.min(toLeft, toRight), Math.min(toTop, toBottom));
            out[0] = 0;
            out[1] = m == toLeft ? -1 : m == toRight ? 1 : 0;
            out[2] = m == toTop ? -1 : m == toBottom ? 1 : 0;
            if (out[1] != 0) {
                out[2] = 0;
            }
            return true;
        }

        double nearX;
        double farX;
        if (Math.abs(dx) < 1e-9) {
            if (px <= minX || px >= maxX) {
                return false;
            }
            nearX = Double.NEGATIVE_INFINITY;
            farX = Double.POSITIVE_INFINITY;
        } else {
            double t1 = (minX - px) / dx;
            double t2 = (maxX - px) / dx;
            nearX = Math.min(t1, t2);
            farX = Math.max(t1, t2);
        }

        double nearY;
        double farY;
        if (Math.abs(dy) < 1e-9) {
            if (py <= minY || py >= maxY) {
                return false;
            }
            nearY = Double.NEGATIVE_INFINITY;
            farY = Double.POSITIVE_INFINITY;
        } else {
            double t1 = (minY - py) / dy;
            double t2 = (maxY - py) / dy;
            nearY = Math.min(t1, t2);
            farY = Math.max(t1, t2);
        }

        double tNear = Math.max(nearX, nearY);
        double tFar = Math.min(farX, farY);

        if (tNear > tFar || tFar < 0 || tNear > 1) {
            return false;
        }

        double t = Math.max(0, tNear);
        out[0] = t;
        if (nearX > nearY) {
            out[1] = dx < 0 ? 1 : -1;
            out[2] = 0;
        } else {
            out[1] = 0;
            out[2] = dy < 0 ? 1 : -1;
        }
        return true;
    }
}
