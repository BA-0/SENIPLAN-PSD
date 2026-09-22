"use client";

import { useEffect, useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { Toaster } from "sonner";
import { useAuthStore } from "@/store/auth-store";

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            staleTime: 15_000,
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      })
  );

  // Les cles des requetes "me" ne portent pas l'utilisateur : sans vidage, un chef de groupe qui
  // se connecte apres un autre dans le meme navigateur verrait (et reenregistrerait dans son
  // groupe) les sections de la direction precedente. Tout changement de compte repart d'un cache vide.
  useEffect(() => {
    let previousUserId = useAuthStore.getState().user?.id ?? null;
    return useAuthStore.subscribe((state) => {
      const userId = state.user?.id ?? null;
      if (userId !== previousUserId) {
        previousUserId = userId;
        queryClient.clear();
      }
    });
  }, [queryClient]);

  return (
    <ThemeProvider attribute="class" defaultTheme="light" enableSystem={false}>
      <QueryClientProvider client={queryClient}>
        {children}
        <Toaster
          position="bottom-right"
          toastOptions={{
            classNames: {
              success: "!bg-primary-50 !text-primary-700 !border-primary-200 dark:!bg-primary-500/15 dark:!text-primary-300 dark:!border-primary-500/30",
              error: "!bg-accent-50 !text-accent-700 !border-accent-200 dark:!bg-accent-500/15 dark:!text-accent-300 dark:!border-accent-500/30",
            },
          }}
        />
      </QueryClientProvider>
    </ThemeProvider>
  );
}
