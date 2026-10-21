import axios, {
  AxiosError,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
  type Method,
} from "axios";
import toast from "react-hot-toast";
import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { useAuthStore } from "@/store/auth";
import type { ApiErrorBody, AuthResponse } from "@/types/api";

export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:9091").replace(
  /\/+$/,
  "",
);

/** Error thrown by `request()` — carries the backend's `message` and `fieldErrors`. */
export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors?: Record<string, string>;
  readonly body?: ApiErrorBody;

  constructor(status: number, message: string, body?: ApiErrorBody) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.body = body;
    this.fieldErrors = body?.fieldErrors;
  }
}

const http = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json", Accept: "application/json" },
  timeout: 30_000,
});

/** Separate instance without interceptors for the refresh call. */
const bare = axios.create({ baseURL: API_BASE_URL, headers: { "Content-Type": "application/json" } });

http.interceptors.request.use((config) => {
  const token = useAuthStore.getState().accessToken;
  if (token) config.headers.set("Authorization", `Bearer ${token}`);
  return config;
});

/** Single in-flight refresh shared by every concurrent 401. */
let refreshPromise: Promise<string | null> | null = null;

function refreshAccessToken(): Promise<string | null> {
  if (!refreshPromise) {
    refreshPromise = (async () => {
      const refreshToken = useAuthStore.getState().refreshToken;
      if (!refreshToken) return null;
      try {
        const { data } = await bare.post<AuthResponse>("/api/auth/refresh", { refreshToken });
        useAuthStore.getState().setSession(data);
        return data.accessToken;
      } catch {
        return null;
      }
    })().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

/**
 * Session is unrecoverable: clear it. <AuthGuard> watches the store and
 * navigates to /login with the Next.js router as soon as the token disappears.
 */
function forceLogout() {
  useAuthStore.getState().clear();
}

type RetriableConfig = InternalAxiosRequestConfig & { _retried?: boolean };

http.interceptors.response.use(
  (r) => r,
  async (error: AxiosError) => {
    const config = error.config as RetriableConfig | undefined;
    const url = config?.url ?? "";
    const isAuthCall = ["/api/auth/login", "/api/auth/refresh", "/api/auth/forgot-password", "/api/auth/reset-password"].some(
      (p) => url.includes(p),
    );
    if (error.response?.status === 401 && config && !config._retried && !isAuthCall) {
      config._retried = true;
      const token = await refreshAccessToken();
      if (token) {
        config.headers.set("Authorization", `Bearer ${token}`);
        return http.request(config);
      }
      forceLogout();
    }
    return Promise.reject(error);
  },
);

function toApiError(err: unknown): ApiError {
  if (err instanceof ApiError) return err;
  if (axios.isAxiosError(err)) {
    const res = err.response;
    if (!res) {
      return new ApiError(0, `Cannot reach the server at ${API_BASE_URL}. Is the backend running?`);
    }
    const body = res.data as Partial<ApiErrorBody> | undefined;
    const message =
      (body && typeof body === "object" && body.message) ||
      (res.status === 403
        ? "You don't have permission to do that."
        : res.status === 429
          ? "Too many requests — please slow down."
          : `Request failed (${res.status})`);
    return new ApiError(res.status, message, body && typeof body === "object" ? (body as ApiErrorBody) : undefined);
  }
  return new ApiError(0, err instanceof Error ? err.message : "Unexpected error");
}

export interface RequestOptions {
  params?: Record<string, unknown>;
  data?: unknown;
  config?: AxiosRequestConfig;
}

/** Strip undefined / null / "" params so the backend sees clean query strings. */
function cleanParams(params?: Record<string, unknown>) {
  if (!params) return undefined;
  const out: Record<string, unknown> = {};
  for (const [k, v] of Object.entries(params)) {
    if (v === undefined || v === null || v === "") continue;
    out[k] = v;
  }
  return out;
}

/** Typed request wrapper. Throws `ApiError`. */
export async function request<T>(method: Method, url: string, opts: RequestOptions = {}): Promise<T> {
  try {
    const res = await http.request<T>({
      method,
      url,
      params: cleanParams(opts.params),
      data: opts.data,
      ...opts.config,
    });
    return res.data;
  } catch (err) {
    throw toApiError(err);
  }
}

export const api = {
  get: <T>(url: string, params?: object) =>
    request<T>("GET", url, { params: params as Record<string, unknown> | undefined }),
  post: <T>(url: string, data?: unknown) => request<T>("POST", url, { data }),
  put: <T>(url: string, data?: unknown) => request<T>("PUT", url, { data }),
  patch: <T>(url: string, data?: unknown) => request<T>("PATCH", url, { data }),
  delete: <T = void>(url: string) => request<T>("DELETE", url),
};

/** Login without the auth interceptor dance. */
export async function login(email: string, password: string): Promise<AuthResponse> {
  return request<AuthResponse>("POST", "/api/auth/login", { data: { email, password } });
}

/** Best-effort server logout, then clear local session. */
export async function logout(): Promise<void> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (refreshToken) {
    try {
      await bare.post("/api/auth/logout", { refreshToken });
    } catch {
      /* ignore */
    }
  }
  useAuthStore.getState().clear();
}

/** Get a user-facing message from any thrown value. */
export function errorMessage(err: unknown): string {
  return toApiError(err).message;
}

/**
 * Show the backend error as a toast and, when a form is given, map `fieldErrors`
 * onto react-hook-form fields.
 */
export function handleApiError<T extends FieldValues>(err: unknown, setError?: UseFormSetError<T>): void {
  const e = toApiError(err);
  if (e.status === 401) return; // handled by the interceptor (logout)
  let mapped = false;
  if (setError && e.fieldErrors) {
    for (const [field, msg] of Object.entries(e.fieldErrors)) {
      setError(field as Path<T>, { type: "server", message: msg });
      mapped = true;
    }
  }
  if (mapped && e.fieldErrors) {
    const first = Object.entries(e.fieldErrors)[0];
    // the server message often *is* the field message (e.g. "This email is already registered") — don't repeat it
    const extra = first && first[1] !== e.message && Object.keys(e.fieldErrors).length === 1 ? ` — ${first[0]}: ${first[1]}` : "";
    toast.error(`${e.message}${extra}`);
  } else {
    toast.error(e.message);
  }
}
