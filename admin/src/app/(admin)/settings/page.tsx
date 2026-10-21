"use client";

import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { CheckCircle2, CircleSlash } from "lucide-react";
import { Button } from "@/components/ui/Button";
import { Card } from "@/components/ui/Card";
import { ErrorState } from "@/components/ui/EmptyState";
import { FormField, FormRow, Input } from "@/components/ui/FormField";
import { PageSpinner } from "@/components/ui/Spinner";
import { AddressAutocomplete } from "@/components/ui/AddressAutocomplete";
import { useApi } from "@/hooks/useApi";
import { handleApiError } from "@/lib/api";
import { cardExpirySchema, cardHint, cardNumberError, expiryText, formatCard, formatExpiry } from "@/lib/card";
import { cleanCountryCode, phoneHint, phoneLengthError, phoneRule } from "@/lib/phone";
import { numberText, req } from "@/lib/schemas";
import { geoApi, settingsApi } from "@/lib/services";
import type { PlaceDetails, Settings, SettingsRequest } from "@/types/api";

const url = z.union([z.literal(""), z.url("Enter a full URL (https://…)")]);

const schema = z
  .object({
    companyName: req("Company name").max(120, "At most 120 characters"),
    contactName: req("Contact person name").max(120, "At most 120 characters"),
    cardHolder: req("Name on card").max(120, "At most 120 characters"),
    cardNumber: z.string().trim(),
    cardExpiry: cardExpirySchema,
    contactCountryCode: z
      .string()
      .trim()
      .min(1, "Required")
      .regex(/^[1-9]\d{0,2}$/, "1–3 digits, e.g. 1"),
    contactPhone: z.string().trim().min(1, "Mobile number is required").regex(/^\d+$/, "Digits only"),
    officeAddress: req("Office address"),
    officeLat: numberText("Latitude"),
    officeLng: numberText("Longitude"),
    officePlaceId: z.string(),
    officePlaceSession: z.string(),
    googleReviewUrl: url,
    facebookReviewUrl: url,
  })
  .superRefine((v, ctx) => {
    const phoneError = phoneLengthError(v.contactCountryCode, v.contactPhone);
    if (phoneError) ctx.addIssue({ code: "custom", path: ["contactPhone"], message: phoneError });
    const cardError = cardNumberError(v.cardNumber);
    if (cardError) ctx.addIssue({ code: "custom", path: ["cardNumber"], message: cardError });
  });
type FormValues = z.infer<typeof schema>;

function toForm(s: Settings): FormValues {
  return {
    companyName: s.companyName ?? "",
    contactName: s.contactName ?? "",
    cardHolder: s.card?.holder ?? "",
    cardNumber: "",
    cardExpiry: s.card ? expiryText(s.card.expMonth, s.card.expYear) : "",
    contactCountryCode: s.contactCountryCode?.replace("+", "") ?? "1",
    contactPhone: s.contactPhone?.replace(/\D/g, "") ?? "",
    officeAddress: s.officeAddress ?? "",
    officeLat: String(s.officeLat ?? ""),
    officeLng: String(s.officeLng ?? ""),
    officePlaceId: "",
    officePlaceSession: "",
    googleReviewUrl: s.googleReviewUrl ?? "",
    facebookReviewUrl: s.facebookReviewUrl ?? "",
  };
}

export default function SettingsPage() {
  const settings = useApi(() => settingsApi.get(), []);
  if (!settings.data) {
    return settings.loading ? (
      <PageSpinner />
    ) : (
      <ErrorState message={settings.error ?? "Failed to load"} onRetry={() => settings.reload()} />
    );
  }
  return <SettingsForm initial={settings.data} onSaved={(s) => settings.setData(() => s)} />;
}

function Indicator({ ok, label, env }: { ok: boolean; label: string; env: string }) {
  return (
    <div className="flex items-start gap-3 rounded-[9px] border border-line p-3">
      {ok ? (
        <CheckCircle2 className="mt-0.5 shrink-0 text-ok" size={20} />
      ) : (
        <CircleSlash className="mt-0.5 shrink-0 text-[#9aa5b4]" size={20} />
      )}
      <div className="min-w-0">
        <div className="font-semibold">{label}</div>
        {ok ? (
          <span className="badge badge-green">Connected</span>
        ) : (
          <span className="badge badge-grey">Not configured — set env vars</span>
        )}
        {!ok && <div className="mt-1 text-[11px] text-muted">{env}</div>}
      </div>
    </div>
  );
}

function SettingsForm({ initial, onSaved }: { initial: Settings; onSaved: (s: Settings) => void }) {
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    reset,
    clearErrors,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormValues>({ resolver: zodResolver(schema), mode: "onTouched", defaultValues: toForm(initial) });

  const code = useWatch({ control, name: "contactCountryCode" });
  const rule = phoneRule(code);
  const card = initial.card;

  const typedCardHint = cardHint(useWatch({ control, name: "cardNumber" }));

  const clean = (name: "contactCountryCode" | "contactPhone" | "cardNumber" | "cardExpiry", value: string) =>
    setValue(name, value, { shouldValidate: !!errors[name], shouldDirty: true });

  // depot address: Google look-up; when the server has no key, free typing is allowed instead
  const [lookupOff, setLookupOff] = useState(false);
  const officeAddress = useWatch({ control, name: "officeAddress" });
  const officePlaceId = useWatch({ control, name: "officePlaceId" });
  const officeVerified = !!officePlaceId || (!!initial.officeAddress && officeAddress.trim() === initial.officeAddress);

  const onPickOffice = (p: PlaceDetails, session: string) => {
    const opts = { shouldDirty: true, shouldValidate: true } as const;
    setValue("officeAddress", p.address, opts);
    setValue("officePlaceId", p.placeId, opts);
    setValue("officePlaceSession", session, opts);
    setValue("officeLat", p.lat.toFixed(6), opts);
    setValue("officeLng", p.lng.toFixed(6), opts);
    clearErrors("officeAddress");
  };

  /** Fills the depot address + coordinates from where this device is now (asks for location permission). */
  const [locating, setLocating] = useState(false);
  const fillCurrentLocation = () => {
    if (!("geolocation" in navigator)) {
      toast.error("This browser can't share its location — enter the latitude and longitude manually");
      return;
    }
    if (!window.isSecureContext) {
      toast.error("Location is only available on a secure (https) page");
      return;
    }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      async (pos) => {
        const { latitude, longitude, accuracy } = pos.coords;
        try {
          // the street address at this spot, as a verified address
          const p = await geoApi.reverse(latitude, longitude);
          onPickOffice(p, "");
          toast.success(`Address found: ${p.address} — click Save settings`, { duration: 6000 });
        } catch {
          const opts = { shouldValidate: true, shouldDirty: true } as const;
          setValue("officeLat", latitude.toFixed(6), opts);
          setValue("officeLng", longitude.toFixed(6), opts);
          toast(`Coordinates set (about ${Math.round(accuracy)} m) — no street address found here, so pick the address from the list`, {
            icon: "ℹ️",
            duration: 7000,
          });
        } finally {
          setLocating(false);
        }
      },
      (err) => {
        setLocating(false);
        toast.error(
          err.code === err.PERMISSION_DENIED
            ? "Location permission was denied — allow it in the browser's address bar and try again"
            : err.code === err.TIMEOUT
              ? "Getting your location took too long — try again"
              : "Couldn't get your location — enter the latitude and longitude manually",
          { duration: 6000 },
        );
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 },
    );
  };

  const onSubmit = async (v: FormValues) => {
    if (!lookupOff && !officeVerified) {
      setError("officeAddress", { type: "manual", message: "Choose the address from the suggestions list" });
      return;
    }
    const body: SettingsRequest = {
      ...v,
      contactCountryCode: `+${v.contactCountryCode}`,
      cardNumber: v.cardNumber ? v.cardNumber.replace(/\s/g, "") : undefined,
      officeLat: Number(v.officeLat),
      officeLng: Number(v.officeLng),
      officePlaceId: v.officePlaceId || undefined,
      officePlaceSession: v.officePlaceSession || undefined,
    };
    try {
      const saved = await settingsApi.update(body);
      onSaved(saved);
      reset(toForm(saved));
      toast.success("Settings saved");
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  const setUp = !!initial.officeAddress && initial.officeLat != null && initial.officeLng != null;

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate>
      {!setUp && (
        <div className="mb-4 rounded-[9px] border border-warn/40 bg-warn/10 p-3 text-sm">
          <b>Finish setting up your company.</b> Check your details below and enter your office / depot address —
          routes start from the depot, and the company name signs every text your customers receive.
        </div>
      )}

      <Card title="🔌 Integrations">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
          <Indicator ok={initial.smsConfigured} label="SMS (Twilio)" env="TWILIO_ACCOUNT_SID / TWILIO_AUTH_TOKEN / TWILIO_FROM" />
          <Indicator ok={initial.googleMapsConfigured} label="Google Maps (geocoding & routing)" env="GOOGLE_MAPS_API_KEY" />
          <Indicator ok={initial.mailConfigured} label="Email (SMTP)" env="SPRING_MAIL_HOST / username / password" />
        </div>
        <p className="demo-note">
          These are read-only and reflect the backend&apos;s environment. Without a provider, messages are logged as
          “Simulated”.
        </p>
      </Card>

      <Card title="🏢 Company">
        <FormRow>
          <FormField label="Company name" error={errors.companyName?.message} hint="Must be unique across all admins">
            <Input placeholder="e.g. Acme Home Services" aria-invalid={!!errors.companyName} {...register("companyName")} />
          </FormField>
          <FormField
            label="Phone number"
            error={errors.contactCountryCode?.message ?? errors.contactPhone?.message}
            hint={errors.contactPhone || errors.contactCountryCode ? undefined : `${phoneHint(code)} · must be unique across all admins`}
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
                  aria-invalid={!!errors.contactCountryCode}
                  {...register("contactCountryCode", {
                    onChange: (e) => clean("contactCountryCode", cleanCountryCode(e.target.value)),
                  })}
                />
              </div>
              <Input
                type="tel"
                inputMode="numeric"
                placeholder="Mobile number"
                className="min-w-0 flex-1"
                aria-invalid={!!errors.contactPhone}
                {...register("contactPhone", {
                  onChange: (e) => clean("contactPhone", e.target.value.replace(/\D/g, "").slice(0, rule.max)),
                })}
              />
            </div>
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Contact person name" error={errors.contactName?.message}>
            <Input aria-invalid={!!errors.contactName} {...register("contactName")} />
          </FormField>
          <FormField label="Email (login)" hint="🔒 Can't be changed here — ask the Super Admin">
            <Input value={initial.loginEmail ?? ""} readOnly disabled />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label="Office / depot address"
            error={errors.officeAddress?.message}
            hint={
              lookupOff
                ? "Address look-up is off on this server — type the full address"
                : officeVerified
                  ? "✓ Verified address"
                  : "Type and pick the address from the list"
            }
          >
            {lookupOff ? (
              <Input placeholder="Street, City, State ZIP" aria-invalid={!!errors.officeAddress} {...register("officeAddress")} />
            ) : (
              <AddressAutocomplete
                value={officeAddress}
                verified={officeVerified}
                aria-invalid={!!errors.officeAddress}
                onTextChange={(text) => {
                  setValue("officeAddress", text, { shouldDirty: true });
                  setValue("officePlaceId", "");
                }}
                onPick={onPickOffice}
                onUnavailable={() => setLookupOff(true)}
              />
            )}
          </FormField>
        </FormRow>
        <FormRow>
          <FormField label="Office latitude" error={errors.officeLat?.message}>
            <Input
              inputMode="decimal"
              placeholder="from the address"
              readOnly={!lookupOff}
              disabled={!lookupOff}
              aria-invalid={!!errors.officeLat}
              {...register("officeLat")}
            />
          </FormField>
          <FormField label="Office longitude" error={errors.officeLng?.message}>
            <Input
              inputMode="decimal"
              placeholder="from the address"
              readOnly={!lookupOff}
              disabled={!lookupOff}
              aria-invalid={!!errors.officeLng}
              {...register("officeLng")}
            />
          </FormField>
          <FormField label="Timezone" hint="Read-only">
            <Input value={initial.timezone} readOnly disabled />
          </FormField>
        </FormRow>
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" size="sm" loading={locating} onClick={fillCurrentLocation}>
            📍 Get current location
          </Button>
          <span className="text-xs text-muted">
            Fills the depot address and its latitude / longitude from where this device is now — use it while you are
            at the office.
          </span>
        </div>
      </Card>

      <Card title="💳 Credit card">
        <FormRow>
          <FormField label="Name on card" error={errors.cardHolder?.message}>
            <Input autoComplete="off" aria-invalid={!!errors.cardHolder} {...register("cardHolder")} />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label={card ? "New card number (optional)" : "Card number"}
            error={errors.cardNumber?.message}
            hint={
              typedCardHint ??
              (card ? `Current: ${card.brand} •••• ${card.last4} — leave blank to keep it` : "Enter your card number")
            }
          >
            <Input
              inputMode="numeric"
              autoComplete="off"
              placeholder="1234 5678 9012 3456"
              aria-invalid={!!errors.cardNumber}
              {...register("cardNumber", { onChange: (e) => clean("cardNumber", formatCard(e.target.value)) })}
            />
          </FormField>
          <FormField label="Expiry (MM/YY)" error={errors.cardExpiry?.message}>
            <Input
              inputMode="numeric"
              autoComplete="off"
              placeholder="MM/YY"
              aria-invalid={!!errors.cardExpiry}
              {...register("cardExpiry", { onChange: (e) => clean("cardExpiry", formatExpiry(e.target.value)) })}
            />
          </FormField>
        </FormRow>
        <p className="demo-note">
          🔒 Only the card brand, last 4 digits, expiry and name on card are saved. The full card number and CVV are
          never stored.
        </p>
      </Card>

      <Card title="⭐ Reviews">
        <FormRow>
          <FormField label="Google review URL" error={errors.googleReviewUrl?.message}>
            <Input placeholder="https://g.page/r/…/review" {...register("googleReviewUrl")} />
          </FormField>
          <FormField label="Facebook review URL" error={errors.facebookReviewUrl?.message}>
            <Input placeholder="https://facebook.com/…/reviews" {...register("facebookReviewUrl")} />
          </FormField>
        </FormRow>
        <p className="demo-note">
          Texted to the customer automatically when a technician completes a job (leave blank to skip the review links).
        </p>
      </Card>

      <div className="sticky bottom-0 -mx-3 flex justify-end gap-2 border-t border-line bg-bg/95 px-3 py-3 backdrop-blur sm:-mx-[22px] sm:px-[22px]">
        <Button variant="secondary" disabled={!isDirty || isSubmitting} onClick={() => reset(toForm(initial))}>
          Discard changes
        </Button>
        <Button type="submit" loading={isSubmitting} disabled={!isDirty && setUp}>
          Save settings
        </Button>
      </div>
    </form>
  );
}
