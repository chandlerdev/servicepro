/** One colour per route on the map and in the stop cards. */
export const ROUTE_COLORS = ["#0e7c6b", "#2c56b0", "#a3541b", "#6b3fb2", "#b03a67", "#1f8a9e", "#7a8b1c", "#c2410c"];

export function routeColor(index: number): string {
  return ROUTE_COLORS[index % ROUTE_COLORS.length];
}
