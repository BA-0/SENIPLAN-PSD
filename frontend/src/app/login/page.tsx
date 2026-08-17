"use client";

import { useState } from "react";
import Image from "next/image";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { AlertCircle } from "lucide-react";

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
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-primary-50 via-slate-50 to-white px-4">
      <div className="w-full max-w-sm">
        <div className="flex justify-center mb-8">
          <div className="bg-white rounded-lg shadow-sm p-3">
            <Image src="/logo-senico.png" alt="SENICO" width={180} height={180} priority className="h-auto w-[180px]" />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-slate-200 shadow-md p-8">
          <h1 className="text-center mb-1">Diagnostic Stratégique</h1>
          <p className="text-center text-[13px] text-slate-500 mb-6">PSD 2027-2031 — Connexion</p>

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="username" required>
                Identifiant
              </Label>
              <Input id="username" autoComplete="username" placeholder="ex. dir.commerciale" error={!!errors.username} {...register("username")} />
              {errors.username && <p className="text-[13px] text-accent-700">{errors.username.message}</p>}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="password" required>
                Mot de passe
              </Label>
              <Input id="password" type="password" autoComplete="current-password" placeholder="••••••••" error={!!errors.password} {...register("password")} />
              {errors.password && <p className="text-[13px] text-accent-700">{errors.password.message}</p>}
            </div>

            {serverError && (
              <div className="flex items-start gap-2 rounded-lg bg-accent-50 border border-accent-100 px-3 py-2.5 text-[13px] text-accent-700">
                <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                <span>{serverError}</span>
              </div>
            )}

            <Button type="submit" variant="primary" size="lg" className="w-full" loading={submitting}>
              Se connecter
            </Button>
          </form>
        </div>

        <p className="text-center text-[13px] text-slate-400 mt-6">
          SENICO SA — Sénégalaise Industrie &amp; Commerce
        </p>
      </div>
    </div>
  );
}
