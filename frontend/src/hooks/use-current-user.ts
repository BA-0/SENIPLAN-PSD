import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";

export function useCurrentUser() {
  const user = useAuthStore((s) => s.user);
  const clear = useAuthStore((s) => s.clear);
  const router = useRouter();

  function logout() {
    clear();
    router.push("/login");
  }

  return { user, logout };
}
