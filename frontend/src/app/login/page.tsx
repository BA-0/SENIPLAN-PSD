"use client";

import { useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { AlertCircle, Eye, EyeOff, Lock, User } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { login } from "@/lib/api/auth";
import { extractErrorMessage } from "@/lib/api-client";
import { useAuthStore } from "@/store/auth-store";

const schema = z.object({
  username: z.string().min(1, "L'identifiant est requis"),
  password: z.string().min(1, "Le mot de passe est requis"),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const setAuth = useAuthStore((s) => s.setAuth);
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    setSubmitting(true);
    try {
      const auth = await login(values.username, values.password);
      setAuth(auth.accessToken, auth.refreshToken, auth.user);
      toast.success(`Bienvenue, ${auth.user.fullName}`);
      router.push(auth.user.role === "ADMIN" ? "/admin" : "/dashboard");
    } catch (error) {
      setServerError(extractErrorMessage(error, "Identifiant ou mot de passe incorrect"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden bg-background flex items-center justify-center px-4">
      <BackgroundDecor />

      <div className="relative z-10 w-full max-w-sm">
        <div className="relative rounded-2xl border border-border/60 bg-card/70 backdrop-blur-xl shadow-xl shadow-primary-900/10 dark:shadow-black/50 p-8 pt-9 animate-fade-in-up overflow-hidden">
          <div className="absolute top-0 inset-x-0 h-1 bg-gradient-to-r from-primary-500 via-accent-500 to-primary-500" />
          <div className="flex justify-center mb-5">
            <Image src="/logo-senico.png" alt="SENICO" width={514} height={98} priority className="h-auto w-[150px]" />
          </div>
          <h1 className="text-center mb-1">Plan Stratégique</h1>
          <p className="text-center text-[13px] text-muted-foreground mb-7">PSD 2027-2031 — Connexion</p>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="username" required>
                Identifiant
              </Label>
              <div className="relative">
                <User className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="username"
                  autoComplete="username"
                  autoFocus
                  error={!!errors.username}
                  className="pl-9"
                  {...register("username")}
                />
              </div>
              {errors.username && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.username.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password" required>
                Mot de passe
              </Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
                <Input
                  id="password"
                  type={showPassword ? "text" : "password"}
                  autoComplete="current-password"
                  error={!!errors.password}
                  className="pl-9 pr-9"
                  {...register("password")}
                />
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  tabIndex={-1}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors"
                  title={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                >
                  {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.password && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.password.message}</p>
              )}
            </div>

            {serverError && (
              <div className="flex items-start gap-2 rounded-lg bg-accent-50 dark:bg-accent-500/10 border border-accent-100 dark:border-accent-500/30 px-3 py-2.5 text-[13px] text-accent-700 dark:text-accent-300 animate-fade-in">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{serverError}</span>
              </div>
            )}

            <Button type="submit" variant="submit" size="lg" className="w-full" loading={submitting}>
              Se connecter
            </Button>
          </form>
        </div>

        <p className="text-center text-[13px] text-muted-foreground mt-6 animate-fade-in">
          SENICO SA — Sénégalaise Industrie &amp; Commerce
        </p>
      </div>
    </div>
  );
}

function BackgroundDecor() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden z-0">
      <div className="absolute -top-24 -left-20 h-80 w-80 rounded-full bg-primary-400/20 dark:bg-primary-500/15 blur-3xl animate-float" />
      <div className="absolute top-1/3 -right-24 h-96 w-96 rounded-full bg-accent-500/10 blur-3xl animate-float-slow" />
      <div
        className="absolute -bottom-28 left-1/4 h-72 w-72 rounded-full bg-sky-400/10 dark:bg-sky-500/10 blur-3xl animate-float"
        style={{ animationDelay: "2s" }}
      />
    </div>
  );
}
