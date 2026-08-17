"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import type { Role } from "@/types/common";
import { useAuthStore } from "@/store/auth-store";

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
    if (requiredRole && user.role !== requiredRole) {
      router.replace(user.role === "ADMIN" ? "/admin" : "/dashboard");
    }
  }, [hydrated, user, requiredRole, router]);

  if (!hydrated || !user || (requiredRole && user.role !== requiredRole)) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-slate-50">
        <div className="h-8 w-8 rounded-full border-2 border-primary-200 border-t-primary-500 animate-spin" />
      </div>
    );
  }

  return <>{children}</>;
}
