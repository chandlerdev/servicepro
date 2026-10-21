"use client";

import { useMemo, useState } from "react";
import toast from "react-hot-toast";
import {
  RouteModal,
  TechnicianModal,
  VehicleModal,
} from "@/components/masters/RouteMasterModals";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type DataColumn } from "@/components/ui/DataTable";
import { Badge } from "@/components/ui/StatusBadge";
import { useApi } from "@/hooks/useApi";
import { ApiError, handleApiError } from "@/lib/api";
import { formatTime } from "@/lib/format";
import { citiesApi, routesApi, techniciansApi, vehiclesApi } from "@/lib/services";
import type { Route, Technician, Vehicle } from "@/types/api";

type Editing =
  | { kind: "route"; item: Route | null }
  | { kind: "vehicle"; item: Vehicle | null }
  | { kind: "tech"; item: Technician | null }
  | null;

type Deleting =
  | { kind: "route"; item: Route }
  | { kind: "vehicle"; item: Vehicle }
  | { kind: "tech"; item: Technician }
  | null;

export default function RoutesPage() {
  const routes = useApi(() => routesApi.list(), []);
  const vehicles = useApi(() => vehiclesApi.list(), []);
  const techs = useApi(() => techniciansApi.list(), []);
  const cities = useApi(() => citiesApi.list(), []);
  const [editing, setEditing] = useState<Editing>(null);
  const [deleting, setDeleting] = useState<Deleting>(null);

  const routeCols = useMemo<DataColumn<Route>[]>(
    () => [
      {
        id: "name",
        header: "Route",
        cell: ({ row }) => <b>{row.original.name}</b>,
      },
      {
        id: "vehicle",
        header: "Vehicle",
        cell: ({ row }) =>
          row.original.vehicleName ? (
            <>
              {row.original.vehicleName} <span className="text-xs text-muted">{row.original.vehiclePlate}</span>
            </>
          ) : (
            "—"
          ),
      },
      {
        id: "techs",
        header: "Resources",
        cell: ({ row }) =>
          row.original.technicians.length
            ? row.original.technicians.map((t) => (
                <span key={t.id} className="pill">
                  {t.name}
                </span>
              ))
            : "—",
      },
      {
        id: "area",
        header: "Service area",
        cell: ({ row }) => (
          <>
            {row.original.areaDescription || "—"}
            {row.original.city && <div className="text-xs text-muted">{row.original.city}</div>}
          </>
        ),
      },
      { id: "start", header: "Start", cell: ({ row }) => formatTime(row.original.startTime) },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: "route", item: row.original })}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleting({ kind: "route", item: row.original })}>
              Delete
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  const vehicleCols = useMemo<DataColumn<Vehicle>[]>(
    () => [
      {
        id: "name",
        header: "Vehicle",
        cell: ({ row }) => (
          <>
            <b>{row.original.name}</b> <span className="text-xs text-muted">{row.original.capacity}</span>
          </>
        ),
      },
      { id: "plate", header: "Plate", cell: ({ row }) => row.original.plate },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: "vehicle", item: row.original })}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleting({ kind: "vehicle", item: row.original })}>
              Del
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  const techCols = useMemo<DataColumn<Technician>[]>(
    () => [
      {
        id: "name",
        header: "Name",
        cell: ({ row }) => (
          <>
            <b>{row.original.name}</b>
            <div className="text-xs text-muted">{row.original.email}</div>
          </>
        ),
      },
      { id: "phone", header: "Phone", cell: ({ row }) => <span className="whitespace-nowrap">{row.original.phone}</span> },
      { id: "skills", header: "Skills", cell: ({ row }) => row.original.skills },
      {
        id: "app",
        header: "App login",
        cell: ({ row }) =>
          row.original.hasLogin ? (
            <Badge tone="green">✓ Enabled</Badge>
          ) : (
            <Badge tone="grey" title="Edit the resource and set a password">
              None — set a password
            </Badge>
          ),
      },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: "tech", item: row.original })}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setDeleting({ kind: "tech", item: row.original })}>
              Del
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  const reloadAll = () => {
    routes.reload({ silent: true });
    vehicles.reload({ silent: true });
    techs.reload({ silent: true });
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    try {
      if (deleting.kind === "route") await routesApi.remove(deleting.item.id);
      if (deleting.kind === "vehicle") await vehiclesApi.remove(deleting.item.id);
      if (deleting.kind === "tech") await techniciansApi.remove(deleting.item.id);
      toast.success(`${deleting.item.name} deleted`);
      setDeleting(null);
      reloadAll();
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        setDeleting(null);
        toast.error(err.message || "This route still has open appointments — reassign them first.");
      } else {
        handleApiError(err);
      }
    }
  };

  return (
    <>
      <Card
        title="🚚 Routes"
        actions={<Button onClick={() => setEditing({ kind: "route", item: null })}>＋ New Route</Button>}
      >
        <DataTable
          columns={routeCols}
          data={routes.data}
          loading={routes.loading}
          error={routes.error}
          onRetry={() => routes.reload()}
          emptyText="No routes yet."
        />
      </Card>

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <Card
          title="🚐 Vehicles"
          actions={
            <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: "vehicle", item: null })}>
              ＋ Add
            </Button>
          }
        >
          <DataTable
            columns={vehicleCols}
            data={vehicles.data}
            loading={vehicles.loading}
            error={vehicles.error}
            onRetry={() => vehicles.reload()}
            emptyText="No vehicles yet."
          />
        </Card>
        <Card
          title="👷 Resources / Technicians"
          actions={
            <Button variant="secondary" size="sm" onClick={() => setEditing({ kind: "tech", item: null })}>
              ＋ Add
            </Button>
          }
        >
          <DataTable
            columns={techCols}
            data={techs.data}
            loading={techs.loading}
            error={techs.error}
            onRetry={() => techs.reload()}
            emptyText="No technicians yet."
          />
        </Card>
      </div>

      {editing?.kind === "route" && (
        <RouteModal
          route={editing.item}
          vehicles={vehicles.data ?? []}
          technicians={techs.data ?? []}
          cities={cities.data ?? []}
          onCitiesChanged={() => cities.reload({ silent: true })}
          onClose={() => setEditing(null)}
          onSaved={reloadAll}
        />
      )}
      {editing?.kind === "vehicle" && (
        <VehicleModal vehicle={editing.item} onClose={() => setEditing(null)} onSaved={reloadAll} />
      )}
      {editing?.kind === "tech" && (
        <TechnicianModal technician={editing.item} onClose={() => setEditing(null)} onSaved={reloadAll} />
      )}

      <ConfirmDialog
        open={!!deleting}
        title={
          deleting?.kind === "route" ? "Delete route" : deleting?.kind === "vehicle" ? "Delete vehicle" : "Delete resource"
        }
        message={
          deleting && (
            <>
              <p>
                Delete <b>{deleting.item.name}</b>?
              </p>
              {deleting.kind === "tech" && (
                <p className="mt-2 text-xs text-muted">They will be removed from any routes they are assigned to.</p>
              )}
              {deleting.kind === "route" && (
                <p className="mt-2 text-xs text-danger">
                  Routes with open appointments can&apos;t be deleted — reassign them first.
                </p>
              )}
            </>
          )
        }
        onClose={() => setDeleting(null)}
        onConfirm={confirmDelete}
      />
    </>
  );
}
