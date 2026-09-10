import { apiClient } from "@/lib/api-client";
import type { AuthResponse } from "@/types/api";

export async function login(username: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/auth/login", { username, password });
  return data;
}

/**
 * Changement par le titulaire lui-même — imposé à la première connexion, possible à tout moment
 * ensuite. Renvoie une session complète : le `user` mis à jour lève le blocage côté interface.
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/auth/change-password", {
    currentPassword,
    newPassword,
  });
  return data;
}
