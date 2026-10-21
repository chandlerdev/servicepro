"use client";

import { useMemo, useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import toast from "react-hot-toast";
import { Button } from "@/components/ui/Button";
import { Card, StatTile } from "@/components/ui/Card";
import { ConfirmDialog } from "@/components/ui/ConfirmDialog";
import { DataTable, type DataColumn } from "@/components/ui/DataTable";
import {
  Checkbox,
  FormField,
  FormRow,
  Input,
  PasswordInput,
} from "@/components/ui/FormField";
import { Modal } from "@/components/ui/Modal";
import { Badge } from "@/components/ui/StatusBadge";
import { useApi } from "@/hooks/useApi";
import { ApiError, handleApiError } from "@/lib/api";
import { formatDateTime } from "@/lib/format";
import {
  cardExpirySchema,
  cardHint,
  cardNumberError,
  expiryText,
  formatCard,
  formatExpiry,
} from "@/lib/card";
import {
  cleanCountryCode,
  formatPhone,
  phoneHint,
  phoneLengthError,
  phoneRule,
} from "@/lib/phone";
import { req } from "@/lib/schemas";
import { superAdminApi } from "@/lib/services";
import type { AdminAccount } from "@/types/api";

/** Super Admin → list of admins. Each admin works in a fully separate space (technicians, customers, routes…). */
export default function AdminsPage() {
  const admins = useApi(() => superAdminApi.list(), []);
  const [editing, setEditing] = useState<AdminAccount | "new" | null>(null);
  const [toDelete, setToDelete] = useState<AdminAccount | null>(null);
  const list = admins.data ?? [];

  const toggleActive = async (a: AdminAccount) => {
    try {
      await superAdminApi.setActive(a.id, !a.active);
      const who = a.companyName ?? a.contactName;
      toast.success(
        a.active
          ? `${who} disabled — they and their technicians can no longer sign in`
          : `${who} enabled`,
      );
      admins.reload({ silent: true });
    } catch (err) {
      handleApiError(err);
    }
  };

  const columns = useMemo<DataColumn<AdminAccount>[]>(
    () => [
      {
        id: "company",
        header: "Company",
        cell: ({ row }) => <b>{row.original.companyName ?? "—"}</b>,
      },
      {
        id: "contact",
        header: "Contact person",
        cell: ({ row }) => (
          <>
            {row.original.contactName}
            <span className="block text-xs whitespace-nowrap text-muted">
              {formatPhone(
                row.original.contactCountryCode,
                row.original.contactPhone,
              )}
            </span>
          </>
        ),
      },
      {
        id: "email",
        header: "Email (login)",
        cell: ({ row }) => row.original.email,
      },
      {
        id: "card",
        header: "Card",
        cell: ({ row }) => {
          const c = row.original.card;
          if (!c) return <span className="text-muted">—</span>;
          return (
            <span className="whitespace-nowrap">
              {c.brand} •••• {c.last4}
              <span className="block text-xs text-muted">
                exp {expiryText(c.expMonth, c.expYear)}
              </span>
            </span>
          );
        },
      },
      {
        id: "techs",
        header: "Technicians",
        cell: ({ row }) => row.original.technicians,
      },
      {
        id: "customers",
        header: "Customers",
        cell: ({ row }) => row.original.customers,
      },
      {
        id: "active",
        header: "Status",
        cell: ({ row }) =>
          row.original.active ? (
            <Badge tone="green">Active</Badge>
          ) : (
            <Badge tone="grey">Disabled</Badge>
          ),
      },
      {
        id: "created",
        header: "Created",
        cell: ({ row }) => formatDateTime(row.original.createdAt),
      },
      {
        id: "actions",
        header: "Actions",
        meta: { className: "text-right whitespace-nowrap" },
        cell: ({ row }) => (
          <div className="inline-flex gap-1">
            <Button
              variant="secondary"
              size="sm"
              onClick={() => setEditing(row.original)}
            >
              Edit
            </Button>
            <Button
              variant={row.original.active ? "warn" : "ok"}
              size="sm"
              onClick={() => toggleActive(row.original)}
            >
              {row.original.active ? "Disable" : "Enable"}
            </Button>
            <Button
              variant="danger"
              size="sm"
              onClick={() => setToDelete(row.original)}
            >
              Delete
            </Button>
          </div>
        ),
      },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  return (
    <>
      <div className="statgrid">
        <StatTile value={list.length} label="Admins" />
        <StatTile value={list.filter((a) => a.active).length} label="Active" />
        <StatTile
          value={list.reduce((n, a) => n + a.technicians, 0)}
          label="Technicians (all admins)"
        />
        <StatTile
          value={list.reduce((n, a) => n + a.customers, 0)}
          label="Customers (all admins)"
        />
      </div>

      <Card
        title="🛡️ Admins"
        actions={
          <Button onClick={() => setEditing("new")}>＋ New Customer</Button>
        }
      >
        <DataTable
          columns={columns}
          data={admins.data}
          loading={admins.loading}
          error={admins.error}
          onRetry={() => admins.reload()}
          emptyText="No admins yet — create the first one."
        />
        <div className="demo-note">
          Each admin signs in to this website and manages their own technicians,
          customers, routes, tasks and appointments — admins never see each
          other&apos;s data. Disabling an admin also blocks their
          technicians&apos; app logins.
        </div>
      </Card>

      {editing && (
        <AdminModal
          admin={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onSaved={() => admins.reload({ silent: true })}
        />
      )}

      <ConfirmDialog
        open={!!toDelete}
        title="Delete admin"
        message={
          toDelete && (
            <>
              <p>
                Delete <b>{toDelete.companyName ?? toDelete.contactName}</b> (
                {toDelete.email})?
              </p>
              <p className="mt-2 text-[13px] text-muted">
                The admin and their technicians will no longer be able to sign
                in, and the admin disappears from this list. Their technicians,
                customers, routes and history are kept in the database (soft
                delete). The email, company name and mobile number can be used
                again for a new admin.
              </p>
            </>
          )
        }
        onClose={() => setToDelete(null)}
        onConfirm={async () => {
          if (!toDelete) return;
          try {
            await superAdminApi.remove(toDelete.id);
            toast.success(
              `${toDelete.companyName ?? toDelete.contactName} deleted`,
            );
            setToDelete(null);
            admins.reload({ silent: true });
          } catch (err) {
            setToDelete(null);
            if (err instanceof ApiError && err.status === 409)
              toast.error(err.message, { duration: 6000 });
            else handleApiError(err);
          }
        }}
      />
    </>
  );
}

// ------------------------------------------------------------------ form

function makeSchema(isNew: boolean) {
  return z
    .object({
      companyName: req("Company name").max(120, "At most 120 characters"),
      contactName: req("Contact person name").max(
        120,
        "At most 120 characters",
      ),
      contactCountryCode: z
        .string()
        .trim()
        .min(1, "Required")
        .regex(/^[1-9]\d{0,2}$/, "1–3 digits, e.g. 1"),
      contactPhone: z
        .string()
        .trim()
        .min(1, "Mobile number is required")
        .regex(/^\d+$/, "Digits only"),
      email: z
        .string()
        .trim()
        .min(1, "Email is required")
        .pipe(z.email("Enter a valid email, e.g. name@company.com")),
      password: isNew
        ? z.string().min(8, "At least 8 characters")
        : z.union([z.literal(""), z.string().min(8, "At least 8 characters")]),
      cardHolder: req("Name on card").max(120, "At most 120 characters"),
      cardNumber: isNew
        ? z.string().trim().min(1, "Card number is required")
        : z.string().trim(),
      cardExpiry: cardExpirySchema,
      active: z.boolean(),
    })
    .superRefine((v, ctx) => {
      // mobile length depends on the country code
      const phoneError = phoneLengthError(v.contactCountryCode, v.contactPhone);
      if (phoneError)
        ctx.addIssue({
          code: "custom",
          path: ["contactPhone"],
          message: phoneError,
        });
      // card length depends on the brand; blank is allowed when editing (keeps the saved card)
      const cardError = cardNumberError(v.cardNumber);
      if (cardError)
        ctx.addIssue({
          code: "custom",
          path: ["cardNumber"],
          message: cardError,
        });
    });
}
type FormValues = z.infer<ReturnType<typeof makeSchema>>;

function Section({ children, first }: { children: string; first?: boolean }) {
  return (
    <h3
      className={`${first ? "" : "mt-4 "}mb-2 text-[11px] font-semibold tracking-[0.6px] text-muted uppercase`}
    >
      {children}
    </h3>
  );
}

function AdminModal({
  admin,
  onClose,
  onSaved,
}: {
  admin: AdminAccount | null;
  onClose: () => void;
  onSaved: () => void;
}) {
  const card = admin?.card;
  const {
    register,
    handleSubmit,
    setError,
    setValue,
    control,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(makeSchema(!admin)),
    mode: "onTouched", // show a field's error as soon as the user leaves it, then live while they fix it
    defaultValues: {
      companyName: admin?.companyName ?? "",
      contactName: admin?.contactName ?? "",
      contactCountryCode: admin?.contactCountryCode?.replace("+", "") ?? "1",
      contactPhone: admin?.contactPhone?.replace(/\D/g, "") ?? "",
      email: admin?.email ?? "",
      password: "",
      cardHolder: card?.holder ?? "",
      cardNumber: "",
      cardExpiry: card ? expiryText(card.expMonth, card.expYear) : "",
      active: admin?.active ?? true,
    },
  });

  const code = useWatch({ control, name: "contactCountryCode" });
  const rule = phoneRule(code);
  const typedCardHint = cardHint(useWatch({ control, name: "cardNumber" }));

  const onSubmit = async (v: FormValues) => {
    const body = {
      ...v,
      email: v.email.trim(),
      contactCountryCode: `+${v.contactCountryCode}`,
      password: v.password || undefined,
      cardNumber: v.cardNumber ? v.cardNumber.replace(/\s/g, "") : undefined,
    };
    try {
      if (admin) await superAdminApi.update(admin.id, body);
      else await superAdminApi.create(body);
      toast.success(
        admin ? "Admin updated" : "Admin created — they can sign in now",
      );
      onSaved();
      onClose();
    } catch (err) {
      handleApiError(err, setError);
    }
  };

  /** Store the cleaned-up value; re-check right away while the field is showing an error. */
  const clean = (
    name: "contactCountryCode" | "contactPhone" | "cardNumber" | "cardExpiry",
    value: string,
  ) => setValue(name, value, { shouldValidate: !!errors[name] });

  return (
    <Modal
      open
      onClose={onClose}
      size="lg"
      title={admin ? "Edit Customer" : "New Customer"}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" form="admin-form" loading={isSubmitting}>
            Save
          </Button>
        </>
      }
    >
      <form id="admin-form" onSubmit={handleSubmit(onSubmit)} noValidate>
        <Section first>Company</Section>
        <FormRow>
          <FormField label="Company name" error={errors.companyName?.message}>
            <Input
              placeholder="e.g. Acme Home Services"
              aria-invalid={!!errors.companyName}
              {...register("companyName")}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label="Contact person name"
            error={errors.contactName?.message}
          >
            <Input
              aria-invalid={!!errors.contactName}
              {...register("contactName")}
            />
          </FormField>
          <FormField
            label="Contact person mobile number"
            error={
              errors.contactCountryCode?.message ?? errors.contactPhone?.message
            }
            hint={phoneHint(code)}
          >
            <div className="flex gap-2">
              <div className="relative w-[84px] shrink-0">
                <span className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-[14px] text-muted">
                  +
                </span>
                <Input
                  inputMode="numeric"
                  autoComplete="tel-country-code"
                  aria-label="Country code"
                  placeholder="1"
                  className="pl-6"
                  aria-invalid={!!errors.contactCountryCode}
                  {...register("contactCountryCode", {
                    // "+1", "1", "001" typed or pasted → "1"
                    onChange: (e) =>
                      clean(
                        "contactCountryCode",
                        cleanCountryCode(e.target.value),
                      ),
                  })}
                />
              </div>
              <Input
                type="tel"
                inputMode="numeric"
                autoComplete="tel-national"
                placeholder="Mobile number"
                className="min-w-0 flex-1"
                aria-invalid={!!errors.contactPhone}
                {...register("contactPhone", {
                  onChange: (e) =>
                    clean(
                      "contactPhone",
                      e.target.value.replace(/\D/g, "").slice(0, rule.max),
                    ),
                })}
              />
            </div>
          </FormField>
        </FormRow>

        <Section>Login</Section>
        <FormRow>
          <FormField label="Email (login)" error={errors.email?.message}>
            <Input
              type="email"
              autoComplete="off"
              placeholder="name@company.com"
              aria-invalid={!!errors.email}
              {...register("email")}
            />
          </FormField>
          <FormField
            label={admin ? "New password (optional)" : "Password"}
            error={errors.password?.message}
            hint={
              admin
                ? "Leave blank to keep the current password"
                : "At least 8 characters"
            }
          >
            <PasswordInput
              autoComplete="new-password"
              aria-invalid={!!errors.password}
              {...register("password")}
            />
          </FormField>
        </FormRow>

        <Section>Credit card</Section>
        <FormRow>
          <FormField label="Name on card" error={errors.cardHolder?.message}>
            <Input
              autoComplete="off"
              aria-invalid={!!errors.cardHolder}
              {...register("cardHolder")}
            />
          </FormField>
        </FormRow>
        <FormRow>
          <FormField
            label={card ? "New card number (optional)" : "Card number"}
            error={errors.cardNumber?.message}
            hint={
              typedCardHint
                ? typedCardHint
                : card
                  ? `Current: ${card.brand} •••• ${card.last4} — leave blank to keep it`
                  : undefined
            }
          >
            <Input
              inputMode="numeric"
              autoComplete="off"
              placeholder="1234 5678 9012 3456"
              aria-invalid={!!errors.cardNumber}
              {...register("cardNumber", {
                onChange: (e) =>
                  clean("cardNumber", formatCard(e.target.value)),
              })}
            />
          </FormField>
          <FormField label="Expiry (MM/YY)" error={errors.cardExpiry?.message}>
            <Input
              inputMode="numeric"
              autoComplete="off"
              placeholder="MM/YY"
              aria-invalid={!!errors.cardExpiry}
              {...register("cardExpiry", {
                onChange: (e) =>
                  clean("cardExpiry", formatExpiry(e.target.value)),
              })}
            />
          </FormField>
        </FormRow>
        <p className="mb-3 text-xs text-muted">
          🔒 Only the card brand, last 4 digits, expiry and name on card are
          saved. The full card number and CVV are never stored.
        </p>

        <Checkbox label="Active (can sign in)" {...register("active")} />
        <p className="mt-3 text-xs text-muted">
          After signing in, the admin sets up their depot address under
          Settings, then adds cities, technicians, vehicles and routes under
          Masters.
        </p>
      </form>
    </Modal>
  );
}
