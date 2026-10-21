/**
 * Types mirroring the ServProWare backend REST API exactly.
 * Dates are `YYYY-MM-DD`, times `HH:mm` (24h), timestamps ISO-8601 strings.
 */

/* ------------------------------------------------------------------ */
/* Enums                                                               */
/* ------------------------------------------------------------------ */
export const ROLES = ["SUPER_ADMIN", "ADMIN", "TECHNICIAN"] as const;
export type Role = (typeof ROLES)[number];

export const APPOINTMENT_STATUSES = [
  "SCHEDULED",
  "CONFIRMED",
  "EN_ROUTE",
  "DELAYED",
  "COMPLETED",
  "CANCELLED",
] as const;
export type AppointmentStatus = (typeof APPOINTMENT_STATUSES)[number];

export const NOTIFICATION_TYPES = ["BOOKING", "SCHEDULE", "UPDATE", "DELAY", "ENROUTE", "REVIEW"] as const;
export type NotificationType = (typeof NOTIFICATION_TYPES)[number];

export const NOTIFICATION_CHANNELS = ["SMS", "EMAIL"] as const;
export type NotificationChannel = (typeof NOTIFICATION_CHANNELS)[number];

export const NOTIFICATION_STATUSES = ["SENT", "FAILED", "SIMULATED"] as const;
export type NotificationStatus = (typeof NOTIFICATION_STATUSES)[number];

/* ------------------------------------------------------------------ */
/* Common                                                              */
/* ------------------------------------------------------------------ */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface PageParams {
  page?: number;
  size?: number;
}

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}

export interface IdName {
  id: number;
  name: string;
}

/* ------------------------------------------------------------------ */
/* Auth                                                                */
/* ------------------------------------------------------------------ */
export interface User {
  id: number;
  name: string;
  email: string;
  role: Role;
  technicianId: number | null;
  active: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: "Bearer";
  expiresIn: number;
  user: User;
}

/* ------------------------------------------------------------------ */
/* Masters                                                             */
/* ------------------------------------------------------------------ */
/** A city on the shared list managed by the Super Admin (GET /api/admin/cities, /api/super/cities). */
/** City Master row (Super Admin): the shared city list with usage across all admins. */
export interface City {
  id: number;
  name: string;
  lat: number;
  lng: number;
  /** active customers in this city */
  customers: number;
  /** routes serving this city */
  routes: number;
}
/** One address suggestion while typing (GET /api/geo/autocomplete). */
export interface AddressSuggestion {
  placeId: string;
  text: string;
  mainText: string | null;
  secondaryText: string | null;
}
/** A verified address picked from the suggestions (GET /api/geo/place/{placeId}). */
export interface PlaceDetails {
  placeId: string;
  address: string;
  lat: number;
  lng: number;
  city: string | null;
  state: string | null;
  postalCode: string | null;
}

/** A city in the admins' City dropdowns (GET /api/admin/cities). */
export interface CityOption {
  id: number;
  name: string;
  lat: number;
  lng: number;
}
export interface CityQuery {
  q?: string;
  page?: number;
  size?: number;
}
export interface CityRequest {
  name: string;
  /** optional — when omitted the backend looks the city up on Google Maps */
  lat?: number | null;
  lng?: number | null;
}

export interface Technician {
  id: number;
  name: string;
  /** ready to show, e.g. "+1 (281) 555-0100" */
  phone: string | null;
  /** e.g. "+1" */
  phoneCountryCode: string | null;
  /** digits only */
  phoneNumber: string | null;
  /** also the app / website login */
  email: string;
  skills: string | null;
  hasLogin: boolean;
}
export interface TechnicianRequest {
  name: string;
  /** "+1" */
  phoneCountryCode: string;
  /** digits only */
  phone: string;
  email: string;
  skills: string;
  /** required on create; blank on update keeps the current password */
  password?: string;
}

export interface Vehicle {
  id: number;
  name: string;
  plate: string;
  capacity: string;
}
export type VehicleRequest = Omit<Vehicle, "id">;

export interface ServiceTask {
  id: number;
  name: string;
  rate: number;
  durationMinutes: number;
}
export type ServiceTaskRequest = Omit<ServiceTask, "id">;

export interface Route {
  id: number;
  name: string;
  vehicleId: number;
  vehicleName: string;
  vehiclePlate: string;
  technicianIds: number[];
  technicians: IdName[];
  city: string;
  areaDescription: string;
  startTime: string;
}
export interface RouteRequest {
  name: string;
  vehicleId: number;
  technicianIds: number[];
  city: string;
  areaDescription: string;
  startTime: string;
}

/* ------------------------------------------------------------------ */
/* Customers                                                           */
/* ------------------------------------------------------------------ */
export interface Customer {
  id: number;
  name: string;
  phone: string;
  email: string | null;
  address: string;
  city: string;
  lat: number;
  lng: number;
  createdAt: string;
}
export interface CustomerRequest {
  name: string;
  phone: string;
  email?: string;
  address: string;
  city: string;
  lat?: number | null;
  lng?: number | null;
  /** the address picked from the suggestions — required when the address is new or changed */
  placeId?: string;
  placeSession?: string;
}
export interface CustomerOption {
  id: number;
  name: string;
  address: string;
  city: string;
}
export interface CustomerQuery extends PageParams {
  q?: string;
  city?: string;
}

/* ------------------------------------------------------------------ */
/* Appointments                                                        */
/* ------------------------------------------------------------------ */
export interface Appointment {
  id: number;
  date: string;
  customerId: number;
  customerName: string;
  customerPhone: string;
  customerAddress: string;
  customerLat: number;
  customerLng: number;
  city: string;
  taskId: number;
  taskName: string;
  taskRate: number;
  taskDuration: number;
  routeId: number;
  routeName: string;
  status: AppointmentStatus;
  notes: string | null;
  stopOrder: number | null;
  plannedArrival: string | null;
  plannedEnd: string | null;
  allocated: boolean;
  createdAt: string;
}
export interface AppointmentCreate {
  date: string;
  customerId: number;
  taskId: number;
  /** null → backend picks the route for the customer's city */
  routeId?: number | null;
  notes?: string;
  sendConfirmation?: boolean;
}
export interface AppointmentUpdate {
  date: string;
  customerId: number;
  taskId: number;
  routeId: number;
  notes: string;
  status: AppointmentStatus;
}
export interface AppointmentQuery extends PageParams {
  date?: string;
  from?: string;
  to?: string;
  routeId?: number;
  status?: AppointmentStatus;
  q?: string;
}

/* ------------------------------------------------------------------ */
/* Schedule                                                            */
/* ------------------------------------------------------------------ */
export interface Office {
  name: string;
  address: string;
  lat: number;
  lng: number;
}
export interface Stop {
  appointmentId: number;
  order: number;
  customerId: number;
  customerName: string;
  customerPhone: string;
  address: string;
  lat: number;
  lng: number;
  taskName: string;
  durationMinutes: number;
  status: AppointmentStatus;
  notes: string | null;
  plannedArrival: string | null;
  plannedEnd: string | null;
  legKm: number | null;
}
export interface RoutePlan {
  routeId: number;
  routeName: string;
  vehicleName: string;
  vehiclePlate: string;
  technicians: IdName[];
  startTime: string;
  optimized: boolean;
  stale: boolean;
  totalKm: number | null;
  endTime: string | null;
  optimizedAt: string | null;
  notifiedAt: string | null;
  googleMapsUrls: string[];
  stops: Stop[];
}
export interface DaySchedule {
  date: string;
  /** null until the admin saves the depot in Settings */
  office: Office | null;
  routes: RoutePlan[];
}
export interface ScheduleRequest {
  date: string;
  routeId?: number;
}
export interface AllocateNotifyResponse {
  schedule: DaySchedule;
  notified: number;
}

/* ------------------------------------------------------------------ */
/* Technician (/api/tech/**)                                           */
/* ------------------------------------------------------------------ */
export interface TechStops extends DaySchedule {
  technician: Technician;
}
export interface TechDelayResponse {
  notified: number;
}
export interface TechCompleteResponse {
  appointment: Stop;
}

/* ------------------------------------------------------------------ */
/* Notifications                                                       */
/* ------------------------------------------------------------------ */
export interface Notification {
  id: number;
  type: NotificationType;
  channel: NotificationChannel;
  status: NotificationStatus;
  toName: string;
  toAddress: string;
  message: string;
  error: string | null;
  appointmentId: number | null;
  createdAt: string;
}
export interface NotificationQuery extends PageParams {
  type?: NotificationType;
}

/* ------------------------------------------------------------------ */
/* Dashboard                                                           */
/* ------------------------------------------------------------------ */
export interface Dashboard {
  date: string;
  appointmentsToday: number;
  completedToday: number;
  delayedToday: number;
  enRouteToday: number;
  customers: number;
  revenueToday: number;
  todays: Appointment[];
  latestNotifications: Notification[];
}

/* ------------------------------------------------------------------ */
/* Settings & users                                                    */
/* ------------------------------------------------------------------ */
/** Empty (nulls) for a new admin until they save their company details. */
export interface Settings {
  companyName: string | null;
  officeAddress: string | null;
  officeLat: number | null;
  officeLng: number | null;
  /** read-only */
  timezone: string;
  googleReviewUrl: string | null;
  facebookReviewUrl: string | null;
  /** editable */
  contactName: string | null;
  /** e.g. "+1" */
  contactCountryCode: string | null;
  /** national number, digits only (editable, duplicate-checked) */
  contactPhone: string | null;
  /** read-only (set by the Super Admin) */
  loginEmail: string | null;
  /** masked; change it with cardHolder / cardNumber / cardExpiry */
  card: CardSummary | null;
  /** read-only */
  smsConfigured: boolean;
  /** read-only */
  googleMapsConfigured: boolean;
  /** read-only */
  mailConfigured: boolean;
}

/** What an admin may change — everything except the login email. */
export interface SettingsRequest {
  companyName: string;
  officeAddress: string;
  officeLat: number;
  officeLng: number;
  googleReviewUrl: string;
  facebookReviewUrl: string;
  /** "+1" */
  contactCountryCode: string;
  contactPhone: string;
  contactName: string;
  cardHolder: string;
  /** blank keeps the saved card */
  cardNumber?: string;
  /** "MM/YY" */
  cardExpiry: string;
  /** the depot address picked from the suggestions — required when the address changes */
  officePlaceId?: string;
  officePlaceSession?: string;
}

/* ------------------------------------------------------------------ */
/* Super Admin → Admins                                                */
/* ------------------------------------------------------------------ */
/** Masked card — the full number and CVV are never stored or returned. */
export interface CardSummary {
  holder: string;
  brand: string;
  last4: string;
  expMonth: number;
  expYear: number;
}
export interface AdminAccount {
  id: number;
  companyName: string | null;
  contactName: string;
  /** e.g. "+1" */
  contactCountryCode: string | null;
  /** national number, digits only */
  contactPhone: string | null;
  email: string;
  active: boolean;
  createdAt: string;
  card: CardSummary | null;
  technicians: number;
  customers: number;
}
export interface AdminAccountRequest {
  companyName: string;
  contactName: string;
  /** "+1" — the backend also accepts "1" */
  contactCountryCode: string;
  /** national number, digits only (length depends on the country) */
  contactPhone: string;
  email: string;
  /** required on create; blank on update keeps the current password */
  password?: string;
  cardHolder: string;
  /** required on create; blank on update keeps the current card */
  cardNumber?: string;
  /** "MM/YY" */
  cardExpiry: string;
  active: boolean;
}
