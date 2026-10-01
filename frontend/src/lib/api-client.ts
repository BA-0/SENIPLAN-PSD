import axios, { AxiosError, type InternalAxiosRequestConfig } from "axios";
import { useAuthStore } from "@/store/auth-store";
import { isNetworkPaused, useNetworkPauseStore } from "@/store/network-pause-store";
import type { ApiErrorBody } from "@/types/api";

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

const NETWORK_PAUSED = "ERR_NETWORK_PAUSED";

/**
 * Blocage du pare-feu AWS devant l'application : il repond 403 ou 429 par une page HTML, alors que
 * l'API repond toujours en JSON (y compris ses propres 403, comme PASSWORD_CHANGE_REQUIRED).
 */
function isGatewayBlock(error: AxiosError): boolean {
  const status = error.response?.status;
  if (status !== 403 && status !== 429) return false;
  const contentType = String(error.response?.headers?.["content-type"] ?? "");
  return contentType.includes("text/html");
}

/** Requete non envoyee : le pare-feu bloque le poste, l'application attend la fin de la pause. */
export function isNetworkPausedError(error: unknown): error is AxiosError {
  return axios.isAxiosError(error) && error.code === NETWORK_PAUSED;
}

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (isNetworkPaused()) {
    return Promise.reject(
      new AxiosError("Connexion momentanément limitée par le pare-feu", NETWORK_PAUSED, config)
    );
  }
  const token = useAuthStore.getState().accessToken;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

let refreshPromise: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = useAuthStore.getState().refreshToken;
  if (!refreshToken) return null;

  try {
    const response = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken });
    const { accessToken } = response.data;
    useAuthStore.getState().setAccessToken(accessToken);
    return accessToken;
  } catch {
    useAuthStore.getState().clear();
    return null;
  }
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;

    if (isGatewayBlock(error)) {
      useNetworkPauseStore.getState().pause();
      return Promise.reject(error);
    }

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry
        && !originalRequest.url?.includes("/auth/")) {
      originalRequest._retry = true;

      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
      }
      const newToken = await refreshPromise;

      if (newToken && originalRequest.headers) {
        originalRequest.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(originalRequest);
      }

      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
    }

    // L'admin a pu reinitialiser le mot de passe d'une session deja ouverte : le serveur ferme
    // alors tout sauf le changement de mot de passe, et l'interface doit y conduire.
    if (error.response?.status === 403
        && (error.response.data as { code?: string } | undefined)?.code === "PASSWORD_CHANGE_REQUIRED"
        && typeof window !== "undefined"
        && !window.location.pathname.startsWith("/change-password")) {
      window.location.href = "/change-password";
    }

    return Promise.reject(error);
  }
);

/** Enregistrement refuse : la section a ete modifiee depuis l'ouverture de la page (cf. SectionVersionConflictException). */
export function isVersionConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && (error.response?.data as ApiErrorBody | undefined)?.code === "VERSION_CONFLICT";
}

export function extractErrorMessage(error: unknown, fallback = "Une erreur est survenue"): string {
  if (isNetworkPausedError(error)) return error.message;
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    return body?.message ?? fallback;
  }
  return fallback;
}
