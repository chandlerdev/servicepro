/** Typed endpoint functions for /api/admin/**, /api/tech/** and /api/auth/**. */
import { api } from "@/lib/api";
import type {
  AdminAccount,
  AdminAccountRequest,
  AllocateNotifyResponse,
  Appointment,
  AppointmentCreate,
  AppointmentQuery,
  AppointmentUpdate,
  AddressSuggestion,
  City,
  CityOption,
  CityQuery,
  CityRequest,
  Customer,
  CustomerOption,
  CustomerQuery,
  CustomerRequest,
  Dashboard,
  DaySchedule,
  Notification,
  NotificationQuery,
  Page,
  PlaceDetails,
  Route,
  RouteRequest,
  ScheduleRequest,
  ServiceTask,
  ServiceTaskRequest,
  Settings,
  SettingsRequest,
  Stop,
  TechCompleteResponse,
  TechDelayResponse,
  TechStops,
  Technician,
  TechnicianRequest,
  User,
  Vehicle,
  VehicleRequest,
} from "@/types/api";

const A = "/api/admin";

/** Generic CRUD helpers for simple master resources. */
function crud<T, R>(base: string) {
  return {
    list: () => api.get<T[]>(base),
    get: (id: number) => api.get<T>(`${base}/${id}`),
    create: (body: R) => api.post<T>(base, body),
    update: (id: number, body: R) => api.put<T>(`${base}/${id}`, body),
    remove: (id: number) => api.delete(`${base}/${id}`),
  };
}

export const authApi = {
  me: () => api.get<User>("/api/auth/me"),
};

/** Super Admin: manage admins. */
export const superAdminApi = {
  ...crud<AdminAccount, AdminAccountRequest>("/api/super/admins"),
  setActive: (id: number, active: boolean) => api.patch<AdminAccount>(`/api/super/admins/${id}/active`, { active }),
};

/** Forgot password (admins and technicians): email a 6-digit code, then reset with it. */
export const passwordResetApi = {
  requestCode: (email: string) => api.post<{ message: string }>("/api/auth/forgot-password", { email }),
  reset: (email: string, code: string, newPassword: string) =>
    api.post<void>("/api/auth/reset-password", { email, code, newPassword }),
};

/** The shared city list, read-only for admins (customer / route / appointment selects). */
/** Address look-up (Google Places via the backend — the key stays on the server). */
export const geoApi = {
  autocomplete: (q: string, session: string) => api.get<AddressSuggestion[]>("/api/geo/autocomplete", { q, session }),
  place: (placeId: string, session?: string) =>
    api.get<PlaceDetails>(`/api/geo/place/${encodeURIComponent(placeId)}`, { session }),
  reverse: (lat: number, lng: number) => api.get<PlaceDetails>("/api/geo/reverse", { lat, lng }),
};

export const citiesApi = {
  list: () => api.get<CityOption[]>(`${A}/cities`),
  /** add a missing city to the shared list (returns the existing one if the name is already there) */
  add: (name: string) => api.post<CityOption>(`${A}/cities`, { name }),
};

/** Super Admin: manage the shared city list (changes reach every admin at once). */
export const superCitiesApi = {
  /** search by name + pages */
  page: (q: CityQuery) => api.get<Page<City>>("/api/super/cities", q),
  create: (body: CityRequest) => api.post<City>("/api/super/cities", body),
  update: (id: number, body: CityRequest) => api.put<City>(`/api/super/cities/${id}`, body),
  remove: (id: number) => api.delete(`/api/super/cities/${id}`),
};

export const vehiclesApi = crud<Vehicle, VehicleRequest>(`${A}/vehicles`);
export const tasksApi = crud<ServiceTask, ServiceTaskRequest>(`${A}/tasks`);
export const routesApi = crud<Route, RouteRequest>(`${A}/routes`);

/** Technicians and their app logins (created / updated in the same request). */
export const techniciansApi = crud<Technician, TechnicianRequest>(`${A}/technicians`);

export const customersApi = {
  page: (q: CustomerQuery) => api.get<Page<Customer>>(`${A}/customers`, q),
  options: () => api.get<CustomerOption[]>(`${A}/customers/options`),
  get: (id: number) => api.get<Customer>(`${A}/customers/${id}`),
  create: (body: CustomerRequest) => api.post<Customer>(`${A}/customers`, body),
  update: (id: number, body: CustomerRequest) => api.put<Customer>(`${A}/customers/${id}`, body),
  remove: (id: number) => api.delete(`${A}/customers/${id}`),
};

export const appointmentsApi = {
  page: (q: AppointmentQuery) => api.get<Page<Appointment>>(`${A}/appointments`, q),
  get: (id: number) => api.get<Appointment>(`${A}/appointments/${id}`),
  create: (body: AppointmentCreate) => api.post<Appointment>(`${A}/appointments`, body),
  update: (id: number, body: AppointmentUpdate) => api.put<Appointment>(`${A}/appointments/${id}`, body),
  remove: (id: number) => api.delete(`${A}/appointments/${id}`),
  sendSms: (id: number) => api.post<Notification>(`${A}/appointments/${id}/send-sms`),
};

export const scheduleApi = {
  get: (q: ScheduleRequest) => api.get<DaySchedule>(`${A}/schedule`, q),
  optimize: (body: ScheduleRequest) => api.post<DaySchedule>(`${A}/schedule/optimize`, body),
  allocateNotify: (body: ScheduleRequest) =>
    api.post<AllocateNotifyResponse>(`${A}/schedule/allocate-notify`, body),
};

export const dashboardApi = {
  get: () => api.get<Dashboard>(`${A}/dashboard`),
};

export const notificationsApi = {
  page: (q: NotificationQuery) => api.get<Page<Notification>>(`${A}/notifications`, q),
  clear: () => api.delete(`${A}/notifications`),
};

/**
 * Technician endpoints — the same API the mobile app uses.
 * `technicianId` is only for ADMINs acting on behalf of a technician; technicians omit it and get their own data.
 */
function techParams(technicianId?: number | null) {
  return technicianId != null ? `?technicianId=${technicianId}` : "";
}

export const techApi = {
  stops: (date: string, technicianId?: number | null) =>
    api.get<TechStops>("/api/tech/stops", { date, technicianId: technicianId ?? undefined }),
  onMyWay: (appointmentId: number, technicianId?: number | null) =>
    api.post<Stop>(`/api/tech/appointments/${appointmentId}/on-my-way${techParams(technicianId)}`),
  delay: (appointmentId: number, minutes: number, reason?: string, technicianId?: number | null) =>
    api.post<TechDelayResponse>(`/api/tech/appointments/${appointmentId}/delay${techParams(technicianId)}`, {
      minutes,
      reason,
    }),
  complete: (appointmentId: number, notes?: string, technicianId?: number | null) =>
    api.post<TechCompleteResponse>(`/api/tech/appointments/${appointmentId}/complete${techParams(technicianId)}`, {
      notes,
    }),
  notifications: (q: { page?: number; size?: number; technicianId?: number | null }) =>
    api.get<Page<Notification>>("/api/tech/notifications", {
      page: q.page,
      size: q.size,
      technicianId: q.technicianId ?? undefined,
    }),
};

export const settingsApi = {
  get: () => api.get<Settings>(`${A}/settings`),
  update: (body: SettingsRequest) => api.put<Settings>(`${A}/settings`, body),
};
