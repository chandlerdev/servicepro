"use client";

import { Fragment, useEffect, useMemo, useState } from "react";
import { APIProvider, AdvancedMarker, InfoWindow, Map, Polyline, useMap } from "@vis.gl/react-google-maps";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { formatTime } from "@/lib/format";
import { cn, mapsDirectionsUrl } from "@/lib/utils";
import type { DaySchedule, Office, Stop } from "@/types/api";
import { routeColor } from "./colors";

const API_KEY = process.env.NEXT_PUBLIC_GOOGLE_MAPS_API_KEY ?? "";
/** Advanced Markers need a Map ID; Google's DEMO_MAP_ID works without any Cloud setup. */
const MAP_ID = "DEMO_MAP_ID";
const MAP_HEIGHT = 460;

interface LatLng {
  lat: number;
  lng: number;
}

function FitBounds({ points }: { points: LatLng[] }) {
  const map = useMap();
  // Only refit when the set of coordinates changes (not on every 20 s poll).
  const key = points.map((p) => `${p.lat.toFixed(5)},${p.lng.toFixed(5)}`).join("|");
  useEffect(() => {
    if (!map || points.length === 0) return;
    if (points.length === 1) {
      map.setCenter(points[0]);
      map.setZoom(12);
      return;
    }
    const b = new google.maps.LatLngBounds();
    points.forEach((p) => b.extend(p));
    map.fitBounds(b, 40);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [map, key]);
  return null;
}

type Selected = { kind: "stop"; stop: Stop; order: number; routeName: string; stale: boolean } | { kind: "depot" };

export function ScheduleMap({ schedule }: { schedule: DaySchedule | undefined }) {
  if (!API_KEY) {
    return (
      <div
        className="flex flex-col items-center justify-center rounded-card border border-dashed border-[#b9c6d6] bg-[#f4f7fa] p-6 text-center"
        style={{ height: MAP_HEIGHT }}
      >
        <div className="mb-2 text-3xl">🗺️</div>
        <b className="mb-1">Map preview unavailable</b>
        <p className="max-w-sm text-[12.5px] text-muted">
          Set <code className="rounded bg-white px-1">NEXT_PUBLIC_GOOGLE_MAPS_API_KEY</code> in <code>.env.local</code>{" "}
          to see pins and route lines. The stop list, ETAs and “Google Maps” route links still work.
        </p>
      </div>
    );
  }
  return (
    <APIProvider apiKey={API_KEY}>
      <MapInner schedule={schedule} />
    </APIProvider>
  );
}

function MapInner({ schedule }: { schedule: DaySchedule | undefined }) {
  const [selected, setSelected] = useState<Selected | null>(null);
  const office: Office | undefined = schedule?.office ?? undefined;

  const routes = useMemo(
    () =>
      (schedule?.routes ?? []).map((r, i) => ({
        plan: r,
        color: routeColor(i),
        stops: r.stops.filter((s) => s.status !== "CANCELLED"),
      })),
    [schedule],
  );

  const points = useMemo(() => {
    const pts: LatLng[] = [];
    if (office) pts.push({ lat: office.lat, lng: office.lng });
    routes.forEach((r) => r.stops.forEach((s) => pts.push({ lat: s.lat, lng: s.lng })));
    return pts;
  }, [office, routes]);

  const center = office ? { lat: office.lat, lng: office.lng } : { lat: 29.76, lng: -95.37 };

  return (
    <div style={{ height: MAP_HEIGHT }} className="overflow-hidden rounded-card border border-line">
      <Map
        mapId={MAP_ID}
        defaultCenter={center}
        defaultZoom={10}
        gestureHandling="cooperative"
        clickableIcons={false}
        style={{ width: "100%", height: "100%" }}
      >
        <FitBounds points={points} />

        {office && (
          <AdvancedMarker
            position={{ lat: office.lat, lng: office.lng }}
            title={office.name}
            onClick={() => setSelected({ kind: "depot" })}
            zIndex={1000}
          >
            <div className="numicon depot">⌂</div>
          </AdvancedMarker>
        )}

        {routes.map(({ plan, color, stops }) => {
          const fresh = plan.optimized && !plan.stale;
          const path = [...(office ? [{ lat: office.lat, lng: office.lng }] : []), ...stops.map((s) => ({ lat: s.lat, lng: s.lng }))];
          return (
            <Fragment key={plan.routeId}>
              {path.length > 1 && (
                <Polyline
                  key={`${plan.routeId}-${fresh ? "solid" : "dashed"}`}
                  path={path}
                  strokeColor={color}
                  strokeWeight={3.5}
                  strokeOpacity={fresh ? 0.75 : 0}
                  icons={
                    fresh
                      ? undefined
                      : [
                          {
                            icon: { path: "M 0,-1 0,1", strokeOpacity: 0.85, strokeColor: color, scale: 3 },
                            offset: "0",
                            repeat: "14px",
                          },
                        ]
                  }
                />
              )}
              {stops.map((s, i) => {
                const cls = s.status === "COMPLETED" ? "done" : s.status === "DELAYED" ? "late" : "";
                return (
                  <AdvancedMarker
                    key={s.appointmentId}
                    position={{ lat: s.lat, lng: s.lng }}
                    title={`#${i + 1} ${s.customerName}`}
                    onClick={() =>
                      setSelected({ kind: "stop", stop: s, order: i + 1, routeName: plan.routeName, stale: !fresh })
                    }
                  >
                    <div className={cn("numicon", cls)} style={cls ? undefined : { background: color }}>
                      {i + 1}
                    </div>
                  </AdvancedMarker>
                );
              })}
            </Fragment>
          );
        })}

        {selected?.kind === "depot" && office && (
          <InfoWindow
            position={{ lat: office.lat, lng: office.lng }}
            pixelOffset={[0, -16]}
            onCloseClick={() => setSelected(null)}
          >
            <div className="text-[13px]">
              <b>{office.name}</b>
              <br />
              {office.address}
            </div>
          </InfoWindow>
        )}
        {selected?.kind === "stop" && (
          <InfoWindow
            position={{ lat: selected.stop.lat, lng: selected.stop.lng }}
            pixelOffset={[0, -16]}
            onCloseClick={() => setSelected(null)}
          >
            <div className="max-w-[240px] space-y-1 text-[13px] leading-snug">
              <div>
                <b>
                  #{selected.order} · {selected.stop.customerName}
                </b>
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {selected.stop.taskName} · <StatusBadge status={selected.stop.status} />
              </div>
              <div className="text-muted">{selected.stop.address}</div>
              <div className="text-xs text-muted">{selected.routeName}</div>
              {selected.stop.plannedArrival && (
                <div>
                  ETA <b>{formatTime(selected.stop.plannedArrival)}</b>
                  {selected.stop.plannedEnd ? ` – ${formatTime(selected.stop.plannedEnd)}` : ""}
                  {selected.stale && <span className="text-xs text-warn"> (needs re-optimize)</span>}
                </div>
              )}
              <a
                href={mapsDirectionsUrl(selected.stop.lat, selected.stop.lng)}
                target="_blank"
                rel="noopener noreferrer"
                className="linklike"
              >
                Directions ↗
              </a>
            </div>
          </InfoWindow>
        )}
      </Map>
    </div>
  );
}
