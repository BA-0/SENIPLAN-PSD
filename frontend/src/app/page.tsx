"use client";

import { useEffect } from "react";
import { homePathFor } from "@/lib/roles";
import { useRouter } from "next/navigation";
import { useAuthStore } from "@/store/auth-store";

export default function RootPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!user) {
      router.replace("/login");
    } else {
      router.replace(homePathFor(user.role));
    }
  }, [user, router]);

  return null;
}
