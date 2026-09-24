"use client";

import Link from "next/link";
import { KeyRound, LogOut, Menu, Volume2, VolumeX } from "lucide-react";
import { canApproveAsDg, canPilot, ROLE_LABELS } from "@/lib/roles";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { useCurrentUser } from "@/hooks/use-current-user";
import { useRealtimeGroup } from "@/hooks/use-realtime-group";
import { useMobileNavStore } from "@/store/mobile-nav-store";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";

export function Header() {
  const { user, logout } = useCurrentUser();
  const voiceEnabled = useVoiceNotificationsStore((s) => s.enabled);
  const toggleVoice = useVoiceNotificationsStore((s) => s.toggle);
  const toggleMobileNav = useMobileNavStore((s) => s.toggle);

  useRealtimeGroup(user?.role === "GROUP_LEADER" ? user.groupId : null);

  const initials = (user?.fullName ?? "")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <header className="h-16 shrink-0 bg-card border-b border-border flex items-center justify-between gap-3 px-4 sm:px-6 sticky top-0 z-10">
      <div className="flex min-w-0 items-center gap-3">
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleMobileNav}
          title="Ouvrir le menu"
          className="shrink-0 lg:hidden"
        >
          <Menu className="h-5 w-5" />
        </Button>
        <div className="min-w-0">
          {/* Le DG n'administre pas : son espace porte son nom et sa mission, pas « Administration ». */}
          <p className="truncate text-sm font-medium text-foreground">
            {user?.groupName ?? (canApproveAsDg(user?.role) ? "Direction Générale" : "Administration")}
          </p>
          <p className="truncate text-[12px] text-muted-foreground">
            {canApproveAsDg(user?.role) ? "Validation du Plan Stratégique" : user?.role ? ROLE_LABELS[user.role] : ""}
          </p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-2 sm:gap-4">
        <div className="flex items-center gap-0.5 rounded-xl border border-border bg-muted/40 p-0.5">
          <ThemeToggle />
          {canPilot(user?.role) && (
            <Button
              variant="ghost"
              size="icon"
              className="h-9 w-9"
              onClick={toggleVoice}
              title={
                voiceEnabled
                  ? "Désactiver les notifications vocales (session projection)"
                  : "Activer les notifications vocales (session projection)"
              }
            >
              {voiceEnabled ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
            </Button>
          )}
          <Button variant="ghost" size="icon" className="h-9 w-9" asChild title="Changer mon mot de passe">
            <Link href="/change-password">
              <KeyRound className="h-4 w-4" />
            </Link>
          </Button>
        </div>

        <div className="hidden h-8 w-px bg-border sm:block" />

        <div className="flex items-center gap-2.5">
          <div className="h-9 w-9 rounded-full bg-primary-100 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300 flex items-center justify-center text-[13px] font-semibold">
            {initials || "?"}
          </div>
          <div className="hidden md:block">
            <p className="text-[13px] font-medium text-foreground leading-tight">{user?.fullName}</p>
            <p className="text-[12px] text-muted-foreground leading-tight">{user?.username}</p>
          </div>
        </div>

        <Button
          variant="destructiveOutline"
          size="sm"
          onClick={logout}
          title="Se déconnecter"
          aria-label="Se déconnecter"
          className="w-9 px-0 sm:w-auto sm:px-3"
        >
          <LogOut className="h-4 w-4" />
          <span className="hidden sm:inline">Déconnexion</span>
        </Button>
      </div>
    </header>
  );
}
