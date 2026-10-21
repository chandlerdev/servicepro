"use client";

import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type DataColumn } from "@/components/ui/DataTable";
import { FormField, FormRow, Input } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { useApi } from "@/hooks/useApi";
import { useDebounce } from "@/hooks/useDebounce";
import { ApiError, handleApiError } from "@/lib/api";
import { optionalCoord, req } from "@/lib/schemas";
import { superCitiesApi } from "@/lib/services";
import type { City } from "@/types/api";

/** Super Admin → City Master: ONE city list shared by every admin; their customers and routes pick from it. */
const PAGE_SIZE = 20;

export default function CitiesPage() {
  const [q, setQ] = useState("");
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q.trim());
  const list = useApi(
    () => superCitiesApi.page({ q: debouncedQ || undefined, page, size: PAGE_SIZE }),
    [debouncedQ, page],
  );
  const [editing, setEditing] = useState<City | "new" | null>(null);
  const [toDelete, setToDelete] = useState<City | null>(null);

  const columns = useMemo<DataColumn<City>[]>(
    () => [
      { id: "name", header: "City", cell: ({ row }) => <b>{row.original.name}</b> },
      {
        id: "centre",
        header: "Map centre",
        cell: ({ row }) => (
          <span className="text-xs text-muted">
            {row.original.lat.toFixed(4)}, {row.original.lng.toFixed(4)}
          </span>
        ),
      },
      { id: "customers", header: "Customers (all admins)", cell: ({ row }) => row.original.customers },
      { id: "routes", header: "Routes (all admins)", cell: ({ row }) => row.original.routes },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button variant="secondary" size="sm" onClick={() => setEditing(row.original)}>
              Edit
            </Button>
            <Button variant="danger" size="sm" onClick={() => setToDelete(row.original)}>
              Delete
            </Button>
          </div>
        ),
      },
    ],
    [],
  );

  return (
    <>
      <Card
        title={
          <>
            🏙️ Cities{" "}
            {list.data && <span className="text-xs font-normal text-muted">{list.data.totalElements} total</span>}
          </>
        }
        actions={<Button onClick={() => setEditing("new")}>＋ New City</Button>}
      >
        <div className="mb-3 flex flex-wrap gap-2">
          <Input
            className="w-[280px]"
            placeholder="Search cities, e.g. Katy"
            aria-label="Search cities"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
          {q && (
            <Button
              variant="secondary"
              onClick={() => {
                setQ("");
                setPage(0);
              }}
            >
              Clear
            </Button>
          )}
        </div>
        <DataTable
          columns={columns}
          data={list.data?.content}
          loading={list.loading}
          error={list.error}
          onRetry={() => list.reload()}
          page={list.data}
          onPageChange={setPage}
          emptyText={debouncedQ ? `No city matches "${debouncedQ}".` : "No cities yet — add the cities your admins serve."}
        />
        <div className="demo-note">
          This one list is shared by every admin: what you add, rename or delete here shows up at once in all admins&apos;
          Customer, Route and Appointment forms (admins can&apos;t change it). Renaming a city also renames it on every
          customer and route that uses it. The map centre is used when a customer&apos;s address can&apos;t be found on
          Google Maps.
        </div>
      </Card>

      {editing && (
        <CityModal
          city={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => list.reload({ silent: true })}
        />
      )}

      <ConfirmDialog
        open={!!toDelete}
        title="Delete city"
        message={
          toDelete && (
            <p>
              Delete <b>{toDelete.name}</b>? Only cities that no admin uses for customers or routes can be deleted.
            </p>
          )
        }
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          if (!toDelete) return;
          try {
            await superCitiesApi.remove(toDelete.id);
            toast.success("City deleted");
            setToDelete(null);
            list.reload({ silent: true });
          } catch (err) {
            setToDelete(null);
            if (err instanceof ApiError && err.status === 409) toast.error(err.message, { duration: 6000 });
            else handleApiError(err);
          }
        }}
      />
    </>
  );
}

const schema = z
  .object({
    name: req("City name"),
    lat: optionalCoord(-90, 90),
    lng: optionalCoord(-180, 180),
  })
  .refine((v) => (v.lat === "") === (v.lng === ""), {
    message: "Enter both latitude and longitude, or leave both blank",
    path: ["lng"],
  });
type FormValues = z.infer<typeof schema>;

function CityModal({ city, onClose, onSaved }: { city: City | null; onClose: () => void; onSaved: () => void }) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: city?.name ?? "",
      lat: city ? String(city.lat) : "",
      lng: city ? String(city.lng) : "",
    },
  });

  const onSubmit = async (v: FormValues) => {
    const body = {
      name: v.name,
      lat: v.lat === "" ? null : Number(v.lat),
      lng: v.lng === "" ? null : Number(v.lng),
    };
    try {
      if (city) await superCitiesApi.update(city.id, body);
      else await superCitiesApi.create(body);
      toast.success(city ? "City updated" : "City added");
      onSaved();
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={city ? "Edit City" : "New City"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="city-form" loading={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="city-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField
            label="City name"
            error={errors.name?.message}
            hint={city ? "Renaming updates every customer and route in this city" : undefined}
          >
            <Input placeholder="e.g. Katy" aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Latitude (optional)" error={errors.lat?.message}>
            <Input inputMode="decimal" placeholder="e.g. 29.7858" aria-invalid={!!errors.lat} {...register("lat")} />
          </FormField>
          <FormField label="Longitude (optional)" error={errors.lng?.message}>
            <Input inputMode="decimal" placeholder="e.g. -95.8245" aria-invalid={!!errors.lng} {...register("lng")} />
          </FormField>
        </FormRow>
        <p className="mt-1 text-xs text-muted">
          Leave latitude and longitude blank to find the city on Google Maps automatically. If the wrong place is
          found, enter the coordinates (right-click the city in Google Maps and copy them).
        </p>
      </form>
    </Modal>
  );
}
