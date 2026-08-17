"use client";

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider } from "next-themes";
import { Toaster } from "sonner";

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
