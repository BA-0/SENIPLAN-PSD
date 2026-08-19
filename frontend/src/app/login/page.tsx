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
import { cn } from "@/lib/utils";
import { useAuthStore } from "@/store/auth-store";

const schema = z.object({
  username: z.string().min(1, "L'identifiant est requis"),
  password: z.string().min(1, "Le mot de passe est requis"),
});

type FormValues = z.infer<typeof schema>;

/*
 * Sur fond sombre, l'autofill de Chrome écrit en gris foncé sur son propre
 * fond clair. `globals.css` neutralise déjà le fond ; ici on repasse le texte
 * et le curseur en blanc, la seule couleur qui tienne sur le panneau.
 */
const AUTOFILL_SOMBRE =
  "[&:-webkit-autofill]:[-webkit-text-fill-color:#fff] [&:-webkit-autofill]:[caret-color:#fff]";

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
    <div className="relative h-screen overflow-hidden bg-[#0A130E]">
      <BackgroundPhoto />

      {/*
        Zonage de la page : tout tient dans un seul écran, sans défilement.
        Sur lg+ l'affiche est le lettrage incrusté dans la photo. Il va de
        40,0 % à 71,0 % en largeur — centre à 50,1 %, soit l'axe de la page : le
        panneau n'a aucun décalage à rattraper. Son bas descend au plus à
        29,6 vh (cas où la photo est cadrée sur la hauteur ; sur écran large elle
        déborde et le lettrage remonte), la réserve de 28 vh passe donc dessous
        dans tous les cas. Sous lg, l'affiche nette revient dans le flux.
      */}
      <div className="relative z-10 mx-auto flex h-screen w-full max-w-[1500px] flex-col items-center overflow-y-auto px-5 pb-[3vh] pt-[4vh]">
        <BrandLockup className="w-[clamp(230px,42vw,430px)] lg:hidden" />
        <div aria-hidden className="hidden shrink-0 lg:block lg:h-[28vh]" />

        <div className="mt-[clamp(16px,4vh,44px)] w-full max-w-[368px] lg:mt-0">
          {/* Panneau opaque : plus de fond translucide flouté, qui brouillait à la
              fois le formulaire et le groupe visible au travers. */}
          <div className="relative overflow-hidden rounded-2xl border border-white/15 bg-[#0E1C15]/92 p-7 shadow-2xl shadow-black/50 animate-fade-in-up">
            {/* Filet de lumière discret : remplace l'ancien bandeau vert épais
                qui coupait le logo. */}
            <div className="absolute inset-x-7 top-0 h-px bg-gradient-to-r from-transparent via-white/30 to-transparent" />

            <h1 className="text-center mb-2.5 text-[24px] leading-tight text-white">Plan Stratégique</h1>
            {/* Le trait vert est descendu : il souligne le titre au lieu de
                traverser le lettrage du logo. */}
            <div className="mx-auto h-[2px] w-14 rounded-full bg-gradient-to-r from-primary-600 via-primary-300 to-primary-600" />
            <p className="text-center text-[13px] text-white/65 mt-2.5 mb-5">PSD 2027-2031 — Connexion</p>

            {/* `autoComplete="off"` : sans lui, le navigateur restaure
                l'identifiant et le mot de passe à chaque rechargement de la
                page, qui restaient donc lisibles pour le suivant à ouvrir le
                poste. */}
            <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" autoComplete="off">
              <div className="space-y-1.5">
                <Label htmlFor="username" required className="text-white/90">
                  Identifiant
                </Label>
                <div className="relative">
                  <User className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-white/50" />
                  <Input
                    id="username"
                    autoComplete="off"
                    autoFocus
                    error={!!errors.username}
                    className={cn(
                      "pl-9 bg-white/10 border-white/25 text-white placeholder:text-white/40 focus:border-white/70 focus:ring-white/20",
                      AUTOFILL_SOMBRE
                    )}
                    {...register("username")}
                  />
                </div>
                {errors.username && (
                  <p className="text-[13px] text-rose-200">{errors.username.message}</p>
                )}
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="password" required className="text-white/90">
                  Mot de passe
                </Label>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-white/50" />
                  <Input
                    id="password"
                    type={showPassword ? "text" : "password"}
                    autoComplete="off"
                    error={!!errors.password}
                    className={cn(
                      "pl-9 pr-9 bg-white/10 border-white/25 text-white placeholder:text-white/40 focus:border-white/70 focus:ring-white/20",
                      AUTOFILL_SOMBRE
                    )}
                    {...register("password")}
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((v) => !v)}
                    tabIndex={-1}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-white/50 hover:text-white transition-colors"
                    title={showPassword ? "Masquer le mot de passe" : "Afficher le mot de passe"}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {errors.password && (
                  <p className="text-[13px] text-rose-200">{errors.password.message}</p>
                )}
              </div>

              {serverError && (
                <div className="flex items-start gap-2 rounded-lg bg-rose-500/15 border border-rose-300/30 px-3 py-2.5 text-[13px] text-rose-100 animate-fade-in">
                  <AlertCircle className="h-4 w-4 shrink-0 mt-0.5" />
                  <span>{serverError}</span>
                </div>
              )}

              <Button
                type="submit"
                variant="submit"
                size="lg"
                className="w-full !mt-5"
                loading={submitting}
              >
                Se connecter
              </Button>
            </form>
          </div>

          <p className="text-center text-[11px] text-white/70 drop-shadow-sm mt-4 animate-fade-in">
            SENICO SA — Sénégalaise Industrie &amp; Commerce
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * Boîte qui reproduit la géométrie d'un `object-cover` de la photo : largeur et
 * hauteur valent au moins celles de l'écran, dans le rapport 1376/768. Tout ce
 * qui est positionné dedans en pourcentage retombe sur les mêmes pixels de la
 * photo, quelle que soit la taille de la fenêtre.
 */
const COVER_BOX =
  "absolute left-1/2 top-1/2 h-full w-full min-h-[55.82vw] min-w-[179.17vh] -translate-x-1/2 -translate-y-1/2";

/**
 * Le mot-symbole net, en tête de page sous lg seulement : à cette largeur le
 * cadrage de la photo coupe le ciel, donc celui qui y est incrusté sort du
 * champ. Sur lg+ c'est le lettrage de la photo qui sert d'affiche.
 */
function BrandLockup({ className }: { className?: string }) {
  return (
    <div className={cn("relative", className)}>
      {/* Halo sombre très diffus : détache le lettrage du ciel sans poser de
          bloc visible autour du logo. */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-1/2 h-[300%] w-[126%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(ellipse_at_center,rgba(3,12,8,0.62)_0%,rgba(3,12,8,0.28)_45%,rgba(3,12,8,0)_72%)] blur-2xl"
      />
      <Image
        src="/logo-senico-mark.png"
        alt="SENICO"
        width={514}
        height={84}
        priority
        className="relative h-auto w-full drop-shadow-[0_14px_34px_rgba(0,0,0,0.6)] animate-fade-in-up"
      />
    </div>
  );
}

function BackgroundPhoto() {
  return (
    <div className="pointer-events-none absolute inset-0 overflow-hidden z-0">
      {/* Le mot-symbole et ses pylônes sont dans la photo, pas posés dessus :
          c'est le ciel de la capture de référence, aucun calque ne vient le
          recouvrir. Seule différence avec `login-bg.jpg` : le lettrage y était
          gris-vert, il est repeint au vert de marque (#2D7A45, celui du bouton
          « Se connecter ») et la vague au rouge (#ED141B). Le masque vient de
          la différence de luminance avec `login-bg-clean.jpg`, où le lettrage
          est effacé — les treillis sont donc conservés au pixel près.
          Régénération : scripts/greenify.ps1. */}
      <div className={COVER_BOX}>
        <Image
          src="/login-bg-vert.jpg"
          alt=""
          fill
          priority
          sizes="100vw"
          className="object-cover [filter:saturate(1.08)_contrast(1.06)]"
        />
      </div>

      {/* Le soleil se lève. Le halo est descendu sur la ligne d'horizon
          (y 33 %), sous le lettrage qui s'arrête à 29,7 % : il réchauffe la mer
          et le bas du ciel sans délaver le SENICO. Dans une COVER_BOX, donc
          collé aux mêmes pixels quelle que soit la fenêtre. */}
      <div className={cn(COVER_BOX, "mix-blend-screen")}>
        <div className="absolute left-[66%] top-[36%] h-[26%] w-[44%] -translate-x-1/2 -translate-y-1/2 rounded-full bg-[radial-gradient(ellipse_at_center,rgba(255,214,140,0.38)_0%,rgba(255,168,66,0.18)_34%,rgba(255,126,42,0.06)_60%,transparent_78%)] blur-2xl" />
      </div>

      {/* Voiles limités au haut (lettrage) et au bas (formulaire, pied de
          page) : la photo reste lisible entre les deux. Celui du haut reste
          léger — au-delà, il éteint le SENICO au lieu de le détacher. */}
      <div className="absolute inset-x-0 top-0 h-[44%] bg-gradient-to-b from-black/30 via-black/10 to-transparent" />
      <div className="absolute inset-x-0 bottom-0 h-[42%] bg-gradient-to-t from-black/55 via-black/18 to-transparent" />
    </div>
  );
}
