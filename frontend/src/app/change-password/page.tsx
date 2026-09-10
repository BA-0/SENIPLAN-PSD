"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { zodResolver } from "@hookform/resolvers/zod";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { toast } from "sonner";
import { AlertCircle, Eye, EyeOff, KeyRound, Lock } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { changePassword } from "@/lib/api/auth";
import { extractErrorMessage } from "@/lib/api-client";
import { useAuthStore } from "@/store/auth-store";
import { homePathFor } from "@/lib/roles";

const schema = z
  .object({
    currentPassword: z.string().min(1, "Le mot de passe actuel est requis"),
    newPassword: z.string().min(8, "Au moins 8 caractères"),
    confirmPassword: z.string().min(1, "Confirmez le nouveau mot de passe"),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ["confirmPassword"],
    message: "Les deux saisies ne correspondent pas",
  })
  .refine((v) => v.newPassword !== v.currentPassword, {
    path: ["newPassword"],
    message: "Choisissez un mot de passe différent de l'actuel",
  });

type FormValues = z.infer<typeof schema>;

/**
 * Ecran hors de l'espace applicatif : un compte qui doit changer son mot de passe n'a acces a
 * rien d'autre, le serveur refusant tout le reste (PasswordChangeGuardFilter). La page sert
 * aussi au changement volontaire, depuis l'en-tete.
 */
export default function ChangePasswordPage() {
  const router = useRouter();
  const user = useAuthStore((s) => s.user);
  const setAuth = useAuthStore((s) => s.setAuth);
  const [serverError, setServerError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPasswords, setShowPasswords] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setHydrated(true);
  }, []);

  useEffect(() => {
    if (hydrated && !user) {
      router.replace("/login");
    }
  }, [hydrated, user, router]);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    setSubmitting(true);
    try {
      const auth = await changePassword(values.currentPassword, values.newPassword);
      setAuth(auth.accessToken, auth.refreshToken, auth.user);
      toast.success("Mot de passe modifié");
      router.replace(homePathFor(auth.user.role));
    } catch (error) {
      setServerError(extractErrorMessage(error, "Échec du changement de mot de passe"));
    } finally {
      setSubmitting(false);
    }
  }

  if (!hydrated || !user) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-background">
        <div className="h-8 w-8 rounded-full border-2 border-primary-200 border-t-primary-500 animate-spin" />
      </div>
    );
  }

  const impose = user.mustChangePassword;

  return (
    <div className="min-h-screen bg-background flex items-center justify-center p-5">
      <Card className="w-full max-w-md">
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <KeyRound className="h-5 w-5 text-primary-600" />
            {impose ? "Choisissez votre mot de passe" : "Changer mon mot de passe"}
          </CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-[13px] text-muted-foreground">
            {impose ? (
              <>
                Le mot de passe qui vous a été remis est connu de la personne qui l&apos;a généré. Remplacez-le pour
                accéder à l&apos;application — vous serez seul(e) à connaître le nouveau.
              </>
            ) : (
              <>Connecté(e) en tant que {user.fullName} ({user.username}).</>
            )}
          </p>

          <form onSubmit={handleSubmit(onSubmit)} className="mt-5 space-y-4">
            {serverError && (
              <div className="flex items-start gap-2 rounded-lg border border-accent-500/40 bg-accent-50 px-3 py-2.5 text-[13px] text-accent-700 dark:bg-accent-500/10 dark:text-accent-300">
                <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>{serverError}</span>
              </div>
            )}

            <div className="space-y-1.5">
              <Label htmlFor="currentPassword">
                {impose ? "Mot de passe reçu" : "Mot de passe actuel"}
              </Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="currentPassword"
                  type={showPasswords ? "text" : "password"}
                  autoComplete="current-password"
                  className="pl-9"
                  error={!!errors.currentPassword}
                  {...register("currentPassword")}
                />
              </div>
              {errors.currentPassword && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.currentPassword.message}</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="newPassword">Nouveau mot de passe</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="newPassword"
                  type={showPasswords ? "text" : "password"}
                  autoComplete="new-password"
                  className="pl-9 pr-10"
                  error={!!errors.newPassword}
                  {...register("newPassword")}
                />
                <button
                  type="button"
                  onClick={() => setShowPasswords((v) => !v)}
                  title={showPasswords ? "Masquer les mots de passe" : "Afficher les mots de passe"}
                  className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                >
                  {showPasswords ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                </button>
              </div>
              {errors.newPassword ? (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.newPassword.message}</p>
              ) : (
                <p className="text-[12px] text-muted-foreground">8 caractères minimum.</p>
              )}
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="confirmPassword">Confirmer le nouveau mot de passe</Label>
              <div className="relative">
                <Lock className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                <Input
                  id="confirmPassword"
                  type={showPasswords ? "text" : "password"}
                  autoComplete="new-password"
                  className="pl-9"
                  error={!!errors.confirmPassword}
                  {...register("confirmPassword")}
                />
              </div>
              {errors.confirmPassword && (
                <p className="text-[13px] text-accent-700 dark:text-accent-300">{errors.confirmPassword.message}</p>
              )}
            </div>

            <Button type="submit" variant="primary" className="w-full" loading={submitting}>
              Enregistrer le nouveau mot de passe
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
