"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";

export default function RootPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!user) {
      router.replace("/login");
    } else {
      router.replace(user.role === "ADMIN" ? "/admin" : "/dashboard");
    }
  }, [user, router]);

  return null;
}
