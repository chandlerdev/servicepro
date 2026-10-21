"use client";

import { Controller, useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import {
  FormField,
  FormRow,
  Input,
  PasswordInput,
} from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { SearchSelect } from "@/components/ui/SearchSelect";
import { Select } from "@/components/ui/Select";
import { handleApiError } from "@/lib/api";
import { addCityFromDropdown } from "@/lib/cities";
import {
  cleanCountryCode,
  phoneHint,
  phoneLengthError,
  phoneRule,
} from "@/lib/phone";
import { idSelect, req, timeText } from "@/lib/schemas";
import { routesApi, techniciansApi, vehiclesApi } from "@/lib/services";
import { cn } from "@/lib/utils";
import type { CityOption, Route, Technician, Vehicle } from "@/types/api";

function Footer({
  formId,
  onClose,
  loading,
  label = "Save",
}: {
  formId: string;
  onClose: () => void;
  loading: boolean;
  label?: string;
}) {
  return (
    <>
      <Button variant="secondary" onClick={onClose}>
        Cancel
      </Button>
      <Button type="submit" form={formId} loading={loading}>
        {label}
      </Button>
    </>
  );
}

/* ------------------------------------------------------------------ */
/* Route                                                               */
/* ------------------------------------------------------------------ */
const routeSchema = z.object({
  name: req("Route name"),
  startTime: timeText,
  vehicleId: idSelect("vehicle"),
  city: z.string().min(1, "Choose a city"),
  areaDescription: z.string(),
  technicianIds: z.array(z.number()).min(1, "Assign at least one resource"),
});
type RouteForm = z.infer<typeof routeSchema>;

export function RouteModal({
  route,
  vehicles,
  technicians,
  cities,
  onClose,
  onSaved,
  onCitiesChanged,
}: {
  route: Route | null;
  vehicles: Vehicle[];
  technicians: Technician[];
  cities: CityOption[];
  onClose: () => void;
  onSaved: () => void;
  /** a city was added from the dropdown — reload the list */
  onCitiesChanged?: () => void;
}) {
  const {
    register,
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<RouteForm>({
    resolver: zodResolver(routeSchema),
    defaultValues: {
      name: route?.name ?? "",
      startTime: route?.startTime?.slice(0, 5) ?? "08:00",
      vehicleId: route
        ? String(route.vehicleId)
        : vehicles[0]
          ? String(vehicles[0].id)
          : "",
      city: route?.city ?? "",
      areaDescription: route?.areaDescription ?? "",
      technicianIds: route?.technicianIds ?? [],
    },
  });

  const onSubmit = async (v: RouteForm) => {
    const body = {
      name: v.name,
      startTime: v.startTime,
      vehicleId: Number(v.vehicleId),
      city: v.city,
      areaDescription: v.areaDescription,
      technicianIds: v.technicianIds,
    };
    try {
      if (route) await routesApi.update(route.id, body);
      else await routesApi.create(body);
      toast.success(route ? "Route updated" : "Route created");
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
      title={route ? "Edit Route" : "New Route"}
      footer={
        <Footer formId="route-form" onClose={onClose} loading={isSubmitting} />
      }
    >
      <form id="route-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField label="Route name" error={errors.name?.message}>
            <Input aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
          <FormField label="Day start time" error={errors.startTime?.message}>
            <Input
              type="time"
              aria-invalid={!!errors.startTime}
              {...register("startTime")}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Vehicle" error={errors.vehicleId?.message}>
            <Select
              aria-invalid={!!errors.vehicleId}
              placeholder="— Select vehicle —"
              options={vehicles.map((v) => ({
                value: v.id,
                label: `${v.name} (${v.plate})`,
              }))}
              {...register("vehicleId")}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label="Primary city"
            error={errors.city?.message}
            hint={
              cities.length === 0
                ? "No cities yet — type a city name and choose “Add”"
                : "Not in the list? Type it and choose “Add”"
            }
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
                  options={cityNames(cities, route?.city).map((c) => ({ value: c, label: c }))}
                  onCreate={(name) => addCityFromDropdown(name, onCitiesChanged)}
                  createLabel={(t) => `Add “${t}” as a new city`}
                />
              )}
            />
          </FormField>
          <FormField
            label="Service area"
            error={errors.areaDescription?.message}
          >
            <Input
              placeholder="e.g. Katy / Cinco Ranch"
              {...register("areaDescription")}
            />
          </FormField>
        </FormRow>
        <FormField
          label="Assign resources (multiple)"
          error={errors.technicianIds?.message}
          className="mb-3"
        >
          <Controller
            control={control}
            name="technicianIds"
            render={({ field }) => (
              <div className="flex flex-wrap gap-2">
                {technicians.length === 0 && (
                  <span className="text-xs text-muted">
                    Add technicians first.
                  </span>
                )}
                {technicians.map((t) => {
                  const on = field.value.includes(t.id);
                  return (
                    <label key={t.id} className={cn("checkchip", on && "on")}>
                      <input
                        type="checkbox"
                        className="accent-brand"
                        checked={on}
                        onChange={(e) =>
                          field.onChange(
                            e.target.checked
                              ? [...field.value, t.id]
                              : field.value.filter((x) => x !== t.id),
                          )
                        }
                      />
                      {t.name}
                    </label>
                  );
                })}
              </div>
            )}
          />
        </FormField>
      </form>
    </Modal>
  );
}

/* ------------------------------------------------------------------ */
/* Vehicle                                                             */
/* ------------------------------------------------------------------ */
const vehicleSchema = z.object({
  name: req("Vehicle name"),
  plate: req("Plate"),
  capacity: z.string(),
});
type VehicleForm = z.infer<typeof vehicleSchema>;

export function VehicleModal({
  vehicle,
  onClose,
  onSaved,
}: {
  vehicle: Vehicle | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<VehicleForm>({
    resolver: zodResolver(vehicleSchema),
    defaultValues: {
      name: vehicle?.name ?? "",
      plate: vehicle?.plate ?? "",
      capacity: vehicle?.capacity ?? "2 techs",
    },
  });
  const onSubmit = async (v: VehicleForm) => {
    try {
      if (vehicle) await vehiclesApi.update(vehicle.id, v);
      else await vehiclesApi.create(v);
      toast.success("Vehicle saved");
      onSaved();
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };
  return (
    <Modal
      open
      size="md"
      onClose={onClose}
      title={vehicle ? "Edit Vehicle" : "New Vehicle"}
      footer={
        <Footer
          formId="vehicle-form"
          onClose={onClose}
          loading={isSubmitting}
        />
      }
    >
      <form id="vehicle-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField label="Name" error={errors.name?.message}>
            <Input aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
          <FormField label="Plate" error={errors.plate?.message}>
            <Input aria-invalid={!!errors.plate} {...register("plate")} />
          </FormField>
          <FormField label="Capacity" error={errors.capacity?.message}>
            <Input {...register("capacity")} />
          </FormField>
        </FormRow>
      </form>
    </Modal>
  );
}

/* ------------------------------------------------------------------ */
/* Technician ("resource") + app login                                 */
/* ------------------------------------------------------------------ */
function makeTechSchema(isNew: boolean) {
  const password = isNew
    ? z
        .string()
        .min(8, "At least 8 characters")
        .max(100, "At most 100 characters")
    : z.union([
        z.literal(""),
        z
          .string()
          .min(8, "At least 8 characters")
          .max(100, "At most 100 characters"),
      ]);
  return z
    .object({
      name: req("Name").max(120, "At most 120 characters"),
      phoneCountryCode: z
        .string()
        .trim()
        .min(1, "Required")
        .regex(/^[1-9]\d{0,2}$/, "1–3 digits, e.g. 1"),
      phone: z
        .string()
        .trim()
        .min(1, "Phone number is required")
        .regex(/^\d+$/, "Digits only"),
      email: z
        .string()
        .trim()
        .min(1, "Email is required")
        .pipe(z.email("Enter a valid email, e.g. name@company.com")),
      skills: z.string().max(200, "At most 200 characters"),
      password,
      confirm: z.string(),
    })
    .superRefine((v, ctx) => {
      const phoneError = phoneLengthError(v.phoneCountryCode, v.phone);
      if (phoneError)
        ctx.addIssue({ code: "custom", path: ["phone"], message: phoneError });
      if (v.password && v.password !== v.confirm) {
        ctx.addIssue({
          code: "custom",
          path: ["confirm"],
          message: "Passwords don't match",
        });
      }
    });
}
type TechForm = z.infer<ReturnType<typeof makeTechSchema>>;

export function TechnicianModal({
  technician,
  onClose,
  onSaved,
}: {
  technician: Technician | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const isNew = !technician;
  // an older technician without a login must get a password now
  const needsPassword = isNew || !technician.hasLogin;
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<TechForm>({
    resolver: zodResolver(makeTechSchema(needsPassword)),
    mode: "onTouched",
    defaultValues: {
      name: technician?.name ?? "",
      phoneCountryCode: technician?.phoneCountryCode?.replace("+", "") ?? "1",
      phone: technician?.phoneNumber?.replace(/\D/g, "") ?? "",
      email: technician?.email ?? "",
      skills: technician?.skills ?? "",
      password: "",
      confirm: "",
    },
  });
  const code = useWatch({ control, name: "phoneCountryCode" });
  const rule = phoneRule(code);
  const clean = (name: "phoneCountryCode" | "phone", value: string) =>
    setValue(name, value, {
      shouldValidate: !!errors[name],
      shouldDirty: true,
    });

  const onSubmit = async (v: TechForm) => {
    const body = {
      name: v.name,
      phoneCountryCode: `+${v.phoneCountryCode}`,
      phone: v.phone,
      email: v.email.trim(),
      skills: v.skills,
      password: v.password || undefined,
    };
    try {
      if (technician) await techniciansApi.update(technician.id, body);
      else await techniciansApi.create(body);
      toast.success(
        isNew
          ? `${v.name} added — they can sign in with ${body.email}`
          : v.password
            ? `${v.name} saved — new password set`
            : `${v.name} saved`,
      );
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
      size="lg"
      title={technician ? "Edit Resource" : "New Resource"}
      footer={
        <Footer formId="tech-form" onClose={onClose} loading={isSubmitting} />
      }
    >
      <form id="tech-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <FormRow>
          <FormField label="Name" error={errors.name?.message}>
            <Input aria-invalid={!!errors.name} {...register("name")} />
          </FormField>
          <FormField
            label="Phone number"
            error={errors.phoneCountryCode?.message ?? errors.phone?.message}
            hint={phoneHint(code)}
          >
            <div className="flex gap-2">
              <div className="relative w-[84px] shrink-0">
                <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-[14px] text-muted">
                  +
                </span>
                <Input
                  inputMode="numeric"
                  aria-label="Country code"
                  placeholder="1"
                  className="pl-6"
                  aria-invalid={!!errors.phoneCountryCode}
                  {...register("phoneCountryCode", {
                    onChange: (e) =>
                      clean(
                        "phoneCountryCode",
                        cleanCountryCode(e.target.value),
                      ),
                  })}
                />
              </div>
              <Input
                type="tel"
                inputMode="numeric"
                placeholder="Phone number"
                className="min-w-0 flex-1"
                aria-invalid={!!errors.phone}
                {...register("phone", {
                  onChange: (e) =>
                    clean(
                      "phone",
                      e.target.value.replace(/\D/g, "").slice(0, rule.max),
                    ),
                })}
              />
            </div>
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label="Email (login)"
            error={errors.email?.message}
            hint="Used to sign in to the app and website"
          >
            <Input
              type="email"
              autoComplete="off"
              placeholder="name@company.com"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
          </FormField>
          <FormField label="Skills (optional)" error={errors.skills?.message}>
            <Input placeholder="HVAC / Electrical" {...register("skills")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label={needsPassword ? "Password" : "New password (optional)"}
            error={errors.password?.message}
            hint={
              needsPassword
                ? "At least 8 characters"
                : "Leave blank to keep the current password"
            }
          >
            <PasswordInput
              autoComplete="new-password"
              aria-invalid={!!errors.password}
              {...register("password")}
            />
          </FormField>
          <FormField label="Confirm password" error={errors.confirm?.message}>
            <PasswordInput
              autoComplete="new-password"
              aria-invalid={!!errors.confirm}
              {...register("confirm")}
            />
          </FormField>
        </FormRow>
        <p className="text-xs text-muted">
          📱 The technician signs in to the ServProWare mobile app (or this
          website) with the email and password above. Changing the password
          signs them out of every device.
        </p>
      </form>
    </Modal>
  );
}

/** City names for the select, keeping a route's current (possibly retired) city selectable. */
function cityNames(cities: CityOption[], current: string | undefined): string[] {
  const names = cities.map((c) => c.name);
  return current && !names.includes(current) ? [...names, current] : names;
}
