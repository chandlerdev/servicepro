package com.servicepros.schedule;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteOptimizerTest {

    /** Points on a line: 0 (depot) at x=0, others at x = 5, 1, 3, 2, 4. Best open path visits them left→right. */
    @Test
    void ordersStopsAlongALine() {
        double[] x = {0, 5, 1, 3, 2, 4};
        double[][] cost = new double[x.length][x.length];
        for (int i = 0; i < x.length; i++) for (int j = 0; j < x.length; j++) cost[i][j] = Math.abs(x[i] - x[j]);

        List<Integer> order = RouteOptimizer.order(cost, 0, List.of(1, 2, 3, 4, 5));

        assertThat(order).containsExactly(2, 4, 3, 5, 1);
        assertThat(RouteOptimizer.pathCost(cost, 0, order)).isEqualTo(5.0);
    }

    @Test
    void twoOptFixesNearestNeighbourTrap() {
        // depot at origin; nearest-neighbour goes to A first then has to zig-zag back
        double[][] pts = {{0, 0}, {1, 0}, {-2, 0}, {6, 0}, {-3, 0}};
        double[][] cost = new double[pts.length][pts.length];
        for (int i = 0; i < pts.length; i++)
            for (int j = 0; j < pts.length; j++)
                cost[i][j] = Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]);

        List<Integer> nn = RouteOptimizer.nearestNeighbour(cost, 0, List.of(1, 2, 3, 4));
        List<Integer> opt = RouteOptimizer.order(cost, 0, List.of(1, 2, 3, 4));

        // NN: 0→1→-2→-3→6 = 1+3+1+9 = 14 ; optimal: 0→-3→-2→1→6 = 3+1+3+5 = 12
        assertThat(RouteOptimizer.pathCost(cost, 0, nn)).isEqualTo(14.0);
        assertThat(RouteOptimizer.pathCost(cost, 0, opt)).isEqualTo(12.0);
    }
}
