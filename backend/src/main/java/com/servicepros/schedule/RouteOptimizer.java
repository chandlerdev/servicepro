package com.servicepros.schedule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Orders stops to minimise total drive time for an open path that starts at a fixed point
 * (depot or last finished stop) and may end anywhere: nearest-neighbour construction followed by
 * 2-opt improvement. Works with asymmetric cost matrices (real road times), so every candidate path
 * is costed in full. Fine for the tens of stops a route has per day.
 */
public final class RouteOptimizer {

    private static final int MAX_PASSES = 50;

    private RouteOptimizer() {
    }

    /**
     * @param cost       cost[i][j] = travel cost from point i to point j
     * @param start      index of the starting point
     * @param candidates indices of the stops to visit
     * @return candidates in visiting order
     */
    public static List<Integer> order(double[][] cost, int start, List<Integer> candidates) {
        List<Integer> path = nearestNeighbour(cost, start, candidates);
        return twoOpt(cost, start, path);
    }

    static List<Integer> nearestNeighbour(double[][] cost, int start, List<Integer> candidates) {
        List<Integer> remaining = new ArrayList<>(candidates);
        List<Integer> path = new ArrayList<>(candidates.size());
        int cur = start;
        while (!remaining.isEmpty()) {
            int bestPos = 0;
            for (int k = 1; k < remaining.size(); k++) {
                if (cost[cur][remaining.get(k)] < cost[cur][remaining.get(bestPos)]) bestPos = k;
            }
            cur = remaining.remove(bestPos);
            path.add(cur);
        }
        return path;
    }

    static List<Integer> twoOpt(double[][] cost, int start, List<Integer> initial) {
        List<Integer> best = new ArrayList<>(initial);
        double bestCost = pathCost(cost, start, best);
        boolean improved = true;
        int passes = 0;
        while (improved && passes++ < MAX_PASSES) {
            improved = false;
            for (int i = 0; i < best.size() - 1; i++) {
                for (int j = i + 1; j < best.size(); j++) {
                    List<Integer> candidate = new ArrayList<>(best);
                    Collections.reverse(candidate.subList(i, j + 1));
                    double c = pathCost(cost, start, candidate);
                    if (c + 1e-9 < bestCost) {
                        best = candidate;
                        bestCost = c;
                        improved = true;
                    }
                }
            }
        }
        return best;
    }

    public static double pathCost(double[][] cost, int start, List<Integer> path) {
        double total = 0;
        int cur = start;
        for (int p : path) {
            total += cost[cur][p];
            cur = p;
        }
        return total;
    }
}
