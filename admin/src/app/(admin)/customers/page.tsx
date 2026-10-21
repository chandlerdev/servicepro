"use client";

import { useMemo, useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type DataColumn } from "@/components/ui/DataTable";
import { FormField, FormRow, Input } from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { AddressAutocomplete } from "@/components/ui/AddressAutocomplete";
import { addCityFromDropdown } from "@/lib/cities";
import { SearchSelect } from "@/components/ui/SearchSelect";
import { useApi } from "@/hooks/useApi";
import { useDebounce } from "@/hooks/useDebounce";
import { handleApiError } from "@/lib/api";
import { optionalCoord, optionalEmail, req } from "@/lib/schemas";
import { citiesApi, customersApi } from "@/lib/services";
import { blankToUndef, mapsPointUrl, optionalNumber } from "@/lib/utils";
import type { CityOption, Customer, PlaceDetails } from "@/types/api";

const PAGE_SIZE = 20;

export default function CustomersPage() {
  const [q, setQ] = useState("");
  const [city, setCity] = useState("");
  const [page, setPage] = useState(0);
  const debouncedQ = useDebounce(q.trim());
  const [editing, setEditing] = useState<Customer | "new" | null>(null);
  const [toDelete, setToDelete] = useState<Customer | null>(null);

  const cities = useApi(() => citiesApi.list(), []);
  const list = useApi(
    () =>
      customersApi.page({
        q: debouncedQ || undefined,
        city: city || undefined,
        page,
        size: PAGE_SIZE,
      }),
    [debouncedQ, city, page],
  );

  const columns = useMemo<DataColumn<Customer>[]>(
    () => [
      { id: "name", header: "Name", cell: ({ row }) => <b>{row.original.name}</b> },
      { id: "city", header: "City", cell: ({ row }) => row.original.city },
      {
        id: "phone",
        header: "Phone",
        cell: ({ row }) => <span className="whitespace-nowrap">{row.original.phone}</span>,
      },
      { id: "address", header: "Address", cell: ({ row }) => row.original.address },
      {
        id: "map",
        header: "Map",
        cell: ({ row }) => (
          <a
            className="linklike text-xs whitespace-nowrap"
            target="_blank"
            rel="noopener noreferrer"
            href={mapsPointUrl(row.original.lat, row.original.lng)}
          >
            📍 Map
          </a>
        ),
      },
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
            👤 Customers{" "}
            {list.data && <span className="text-xs font-normal text-muted">{list.data.totalElements} total</span>}
          </>
        }
        actions={<Button onClick={() => setEditing("new")}>＋ New Customer</Button>}
      >
        <div className="mb-3 flex flex-wrap gap-2">
          <Input
            className="w-[260px]"
            placeholder="Search name, city, address…"
            value={q}
            onChange={(e) => {
              setQ(e.target.value);
              setPage(0);
            }}
          />
          <SearchSelect
            className="w-[220px]"
            value={city}
            onChange={(v) => {
              setCity(v);
              setPage(0);
            }}
            placeholder="All cities"
            clearable
            searchPlaceholder="Search cities…"
            options={(cities.data ?? []).map((c) => ({ value: c.name, label: c.name }))}
            aria-label="Filter by city"
          />
        </div>
        <DataTable
          columns={columns}
          data={list.data?.content}
          loading={list.loading}
          error={list.error}
          onRetry={() => list.reload()}
          page={list.data}
          onPageChange={setPage}
          emptyText="No customers found."
        />
        <div className="demo-note">
          Address changes are geocoded automatically and flag affected schedules for re-optimization + customer
          re-notification.
        </div>
      </Card>

      {editing && (
        <CustomerModal
          customer={editing === "new" ? null : editing}
          cities={cities.data ?? []}
          onClose={() => setEditing(null)}
          onSaved={() => list.reload({ silent: true })}
          onCitiesChanged={() => cities.reload({ silent: true })}
        />
      )}

      <ConfirmDialog
        open={!!toDelete}
        title="Delete customer"
        message={
          toDelete && (
            <>
              <p>
                Delete <b>{toDelete.name}</b>?
              </p>
              <p className="mt-2 text-xs text-danger">Any open appointments for this customer will be cancelled.</p>
            </>
          )
        }
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          if (!toDelete) return;
          try {
            await customersApi.remove(toDelete.id);
            toast.success("Customer deleted");
            setToDelete(null);
            list.reload({ silent: true });
          } catch (err) {
            handleApiError(err);
          }
        }}
      />
    </>
  );
}

const schema = z.object({
  name: req("Name"),
  phone: req("Phone"),
  email: optionalEmail,
  address: req("Address"),
  city: z.string().min(1, "Choose a city"),
  lat: optionalCoord(-90, 90),
  lng: optionalCoord(-180, 180),
  placeId: z.string(),
  placeSession: z.string(),
});
type FormValues = z.infer<typeof schema>;

function CustomerModal({
  customer,
  cities,
  onClose,
  onSaved,
  onCitiesChanged,
}: {
  customer: Customer | null;
  cities: CityOption[];
  onClose: () => void;
  onSaved: (c: Customer) => void;
  /** a city was added from the dropdown — reload the list */
  onCitiesChanged?: () => void;
}) {
  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    clearErrors,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      placeId: "",
      placeSession: "",
      name: customer?.name ?? "",
      phone: customer?.phone ?? "",
      email: customer?.email ?? "",
      address: customer?.address ?? "",
      city: customer?.city ?? "",
      lat: customer?.lat != null ? String(customer.lat) : "",
      lng: customer?.lng != null ? String(customer.lng) : "",
    },
  });

  // Google address look-up; when the server has no key, free typing is allowed instead
  const [lookupOff, setLookupOff] = useState(false);
  const addressValue = useWatch({ control, name: "address" });
  const placeId = useWatch({ control, name: "placeId" });
  const addressVerified = !!placeId || (!!customer && addressValue.trim() === customer.address);

  const onPickAddress = (p: PlaceDetails, session: string) => {
    const opts = { shouldDirty: true } as const;
    setValue("address", p.address, opts);
    setValue("placeId", p.placeId, opts);
    setValue("placeSession", session, opts);
    setValue("lat", p.lat.toFixed(6), opts);
    setValue("lng", p.lng.toFixed(6), opts);
    clearErrors("address");
    // pre-select the city when Google's town is on the city list
    const match = p.city ? cities.find((c) => c.name.toLowerCase() === p.city!.toLowerCase()) : undefined;
    if (match) {
      setValue("city", match.name, { ...opts, shouldValidate: true });
    } else if (p.city) {
      // the address's town isn't on the list yet — add it and select it
      addCityFromDropdown(p.city, onCitiesChanged)
        .then((name) => setValue("city", name, { ...opts, shouldValidate: true }))
        .catch(() => toast(`"${p.city}" couldn't be added — choose the closest city`, { icon: "ℹ️" }));
    }
  };

  const onSubmit = async (v: FormValues) => {
    if (!lookupOff && !addressVerified) {
      setError("address", { type: "manual", message: "Choose the address from the suggestions list" });
      return;
    }
    const body = {
      name: v.name,
      phone: v.phone,
      email: blankToUndef(v.email),
      address: v.address,
      city: v.city,
      lat: optionalNumber(v.lat),
      lng: optionalNumber(v.lng),
      placeId: v.placeId || undefined,
      placeSession: v.placeSession || undefined,
    };
    try {
      const saved = customer ? await customersApi.update(customer.id, body) : await customersApi.create(body);
      toast.success(customer ? "Customer updated" : "Customer created");
      onSaved(saved);
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  return (
    <Modal
      open
      onClose={onClose}
      title={customer ? "Edit Customer" : "New Customer"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="customer-form" loading={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="customer-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField label="Full name" error={errors.name?.message}>
            <Input aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
          <FormField label="Phone (SMS)" error={errors.phone?.message}>
            <Input type="tel" aria-invalid={!!errors.phone} {...register("phone")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Email" error={errors.email?.message}>
            <Input type="email" aria-invalid={!!errors.email} {...register("email")} />
          </FormField>
          <FormField
            label="City / service area"
            error={errors.city?.message}
            hint={cities.length === 0 ? "No cities yet — type a city name and choose “Add”" : undefined}
          >
            <Controller
              control={control}
              name="city"
              render={({ field }) => (
                <SearchSelect
                  value={field.value}
                  onChange={field.onChange}
                  onBlur={field.onBlur}
                  aria-invalid={!!errors.city}
                  placeholder="— Select city —"
                  searchPlaceholder="Search or type a new city…"
                  options={withCurrent(cities, customer?.city).map((c) => ({ value: c, label: c }))}
                  onCreate={(name) => addCityFromDropdown(name, onCitiesChanged)}
                  createLabel={(t) => `Add “${t}” as a new city`}
                />
              )}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label="Service address"
            error={errors.address?.message}
            hint={
              lookupOff
                ? "Address look-up is off on this server — type the full address"
                : addressVerified
                  ? "✓ Verified address"
                  : "Type and pick the address from the list"
            }
          >
            {lookupOff ? (
              <Input placeholder="Street, city, state ZIP" aria-invalid={!!errors.address} {...register("address")} />
            ) : (
              <AddressAutocomplete
                value={addressValue}
                verified={addressVerified}
                aria-invalid={!!errors.address}
                onTextChange={(text) => {
                  setValue("address", text, { shouldDirty: true });
                  setValue("placeId", "");
                }}
                onPick={onPickAddress}
                onUnavailable={() => setLookupOff(true)}
              />
            )}
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Latitude" error={errors.lat?.message}>
            <Input
              inputMode="decimal"
              placeholder="auto"
              readOnly={!lookupOff}
              disabled={!lookupOff}
              aria-invalid={!!errors.lat}
              {...register("lat")}
            />
          </FormField>
          <FormField label="Longitude" error={errors.lng?.message}>
            <Input
              inputMode="decimal"
              placeholder="auto"
              readOnly={!lookupOff}
              disabled={!lookupOff}
              aria-invalid={!!errors.lng}
              {...register("lng")}
            />
          </FormField>
        </FormRow>
        <p className="-mt-1 mb-3 text-[11.5px] text-muted">
          {lookupOff
            ? "Lat/Lng are optional — leave blank to geocode automatically from the address."
            : "Latitude and longitude are filled in from the chosen address."}
        </p>
      </form>
    </Modal>
  );
}

/** City names for the select, keeping a customer's current (possibly retired) city selectable. */
function withCurrent(cities: CityOption[], current: string | undefined): string[] {
  const names = cities.map((c) => c.name);
  return current && !names.includes(current) ? [...names, current] : names;
}
