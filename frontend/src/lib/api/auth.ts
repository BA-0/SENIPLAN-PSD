import { apiClient } from "@/lib/api-client";
import type { AuthResponse } from "@/types/api";

export async function login(username: string, password: string): Promise<AuthResponse> {
  const { data } = await apiClient.post<AuthResponse>("/auth/login", { username, password });
  return data;
}
