package utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * DeterminizationEngine
 * ---------------------
 *
 * Port of TAG's smartDeterminiseGrid() and updateHeatMap() adapted to the Ludii architecture.
 *
 * Generates plausible hypotheses about the opponent's grid while respecting known HITs/MISSes constraints.
 *
 * @author LECHAT Jérôme
 */
public class DeterminizationEngine
{
    private static final int[] SHIP_SIZES = {5, 4, 3, 3, 2};
    private static final int GRID_SIZE = 10;
    private static final int MAX_ATTEMPTS = 10000;
    private static final int PLACEMENT_TRIES = 50;

    private final boolean[][] hitMap;
    private final boolean[][] missMap;
    private final Random rnd;

    public DeterminizationEngine(final boolean[][] hitMap, final boolean[][] missMap, final Random rnd)
    {
        this.hitMap = hitMap;
        this.missMap = missMap;
        this.rnd = rnd;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Return the size of the grid
     */
    public int getGridSize()
    {
        return GRID_SIZE;
    }

    /**
     * Return the hitMap
     */
    public boolean[][] getHitMap()
    {
        return hitMap;
    }

    /**
     * Generates a probability distribution on the grid
     * Port of TAG's updateHeatMap()
     */
    public double[][] generateHeatMap(final int iterations)
    {
        final double[][] heatMap = new double[GRID_SIZE][GRID_SIZE];

        for (int i = 0; i < iterations; i++)
        {
            final boolean[][] hypothesis = solveCSP(null);
            for (int x = 0; x < GRID_SIZE; x++)
                for (int y = 0; y < GRID_SIZE; y++)
                    if (hypothesis[x][y])
                        heatMap[x][y] += 1.0;
        }

        for (int x = 0; x < GRID_SIZE; x++)
            for (int y = 0; y < GRID_SIZE; y++)
                heatMap[x][y] /= iterations;

        return heatMap;
    }

    /**
     * Generates a consistent hypothesis of the opponent's grid
     * Port of TAG's smartDeterminiseGrid()
     *
     * @param heatMap (optional) to sort HITs by probability
     * @return a boolean[10][10] grid where true = ship present
     */
    public boolean[][] solveCSP(final double[][] heatMap)
    {
        final boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];

        final List<int[]> hitCoords = new ArrayList<>();
        for (int x = 0; x < GRID_SIZE; x++)
            for (int y = 0; y < GRID_SIZE; y++)
                if (hitMap[x][y])
                    hitCoords.add(new int[]{x, y});

        final List<Integer> shipsToPlace = new ArrayList<>();
        for (final int size : SHIP_SIZES)
            shipsToPlace.add(size);

        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++)
        {
            for (int x = 0; x < GRID_SIZE; x++)
                for (int y = 0; y < GRID_SIZE; y++)
                    grid[x][y] = false;

            Collections.shuffle(shipsToPlace, rnd);

            final List<int[]> uncoveredHits = new ArrayList<>(hitCoords);

            if (heatMap != null && !uncoveredHits.isEmpty())
                uncoveredHits.sort((a, b) ->
                    Double.compare(heatMap[b[0]][b[1]], heatMap[a[0]][a[1]]));

            boolean success = true;

            for (final int shipSize : shipsToPlace)
            {
                boolean placed = false;

                if (!uncoveredHits.isEmpty())
                {
                    for (int k = 0; k < PLACEMENT_TRIES; k++)
                    {
                        final int[] target = uncoveredHits.get(0);
                        final boolean horiz = rnd.nextBoolean();
                        final int offset = rnd.nextInt(shipSize);
                        final int startX = horiz ? target[0] - offset : target[0];
                        final int startY = horiz ? target[1] : target[1] - offset;

                        if (canPlace(grid, startX, startY, shipSize, horiz))
                        {
                            placeShip(grid, startX, startY, shipSize, horiz);
                            removeCoveredHits(uncoveredHits, startX, startY, shipSize, horiz);
                            placed = true;
                            break;
                        }
                    }
                }
                else
                {
                    for (int k = 0; k < PLACEMENT_TRIES; k++)
                    {
                        final int x = rnd.nextInt(GRID_SIZE);
                        final int y = rnd.nextInt(GRID_SIZE);
                        final boolean horiz = rnd.nextBoolean();

                        if (canPlace(grid, x, y, shipSize, horiz))
                        {
                            placeShip(grid, x, y, shipSize, horiz);
                            placed = true;
                            break;
                        }
                    }
                }

                if (!placed) { success = false; break; }
            }

            if (success && uncoveredHits.isEmpty())
                return grid;
        }

        return randomizeGrid();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean[][] randomizeGrid()
    {
        final boolean[][] grid = new boolean[GRID_SIZE][GRID_SIZE];
        final boolean[][] emptyMiss = new boolean[GRID_SIZE][GRID_SIZE];

        for (final int size : SHIP_SIZES)
        {
            boolean placed = false;
            while (!placed)
            {
                final int x = rnd.nextInt(GRID_SIZE);
                final int y = rnd.nextInt(GRID_SIZE);
                final boolean h = rnd.nextBoolean();
                if (canPlaceWithMiss(grid, x, y, size, h, emptyMiss))
                {
                    placeShip(grid, x, y, size, h);
                    placed = true;
                }
            }
        }
        return grid;
    }

    private boolean canPlace(final boolean[][] grid, final int x, final int y,
                              final int size, final boolean horizontal)
    {
        return canPlaceWithMiss(grid, x, y, size, horizontal, missMap);
    }

    private boolean canPlaceWithMiss(final boolean[][] grid, final int x, final int y,
                                      final int size, final boolean horizontal,
                                      final boolean[][] miss)
    {
        if (horizontal)
        {
            if (x < 0 || x + size > GRID_SIZE || y < 0 || y >= GRID_SIZE) return false;
            for (int i = 0; i < size; i++)
                if (miss[x+i][y] || grid[x+i][y]) return false;
        }
        else
        {
            if (y < 0 || y + size > GRID_SIZE || x < 0 || x >= GRID_SIZE) return false;
            for (int i = 0; i < size; i++)
                if (miss[x][y+i] || grid[x][y+i]) return false;
        }
        return true;
    }

    private void placeShip(final boolean[][] grid, final int x, final int y,
                            final int size, final boolean horizontal)
    {
        for (int i = 0; i < size; i++)
            if (horizontal) grid[x+i][y] = true;
            else grid[x][y+i] = true;
    }

    private void removeCoveredHits(final List<int[]> hits, final int x, final int y,
                                    final int size, final boolean horizontal)
    {
        for (int i = hits.size() - 1; i >= 0; i--)
        {
            final int[] h = hits.get(i);
            final boolean covered = horizontal
                ? (h[1] == y && h[0] >= x && h[0] < x + size)
                : (h[0] == x && h[1] >= y && h[1] < y + size);
            if (covered) hits.remove(i);
        }
    }
}
