"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { Role } from "@/types/common";
import { useAuthStore } from "@/store/auth-store";
import { canPilot, homePathFor } from "@/lib/roles";

/**
 * `requiredRole="ADMIN"` protege l'espace de pilotage : la direction generale y a acces au
 * meme titre que l'admin. Un role exige autre chose reste compare a l'identique.
 */
function satisfies(role: Role | undefined, requiredRole?: Role): boolean {
  if (!requiredRole) return true;
  if (requiredRole === "ADMIN") return canPilot(role);
  return role === requiredRole;
}

export function AuthGuard({ requiredRole, children }: { requiredRole?: Role; children: React.ReactNode }) {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    if (!user) {
      router.replace("/login");
      return;
    }
    if (!satisfies(user.role, requiredRole)) {
      router.replace(homePathFor(user.role));
    }
  }, [hydrated, user, requiredRole, router]);

  if (!hydrated || !user || !satisfies(user.role, requiredRole)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="h-8 w-8 rounded-full border-2 border-primary-200 border-t-primary-500 animate-spin" />
      </div>
    );
  }

  return <>{children}</>;
}
