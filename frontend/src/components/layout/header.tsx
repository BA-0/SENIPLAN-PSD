"use client";

import { LogOut, Menu, Volume2, VolumeX } from "lucide-react";
import { canPilot, ROLE_LABELS } from "@/lib/roles";
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
          <p className="truncate text-sm font-medium text-foreground">{user?.groupName ?? "Administration"}</p>
          <p className="truncate text-[12px] text-muted-foreground">
            {user?.role ? ROLE_LABELS[user.role] : ""}
          </p>
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1.5 sm:gap-3">
        <div className="flex items-center gap-2.5">
          <div className="h-9 w-9 rounded-full bg-primary-100 text-primary-700 dark:bg-primary-500/15 dark:text-primary-300 flex items-center justify-center text-[13px] font-semibold">
            {initials || "?"}
          </div>
          <div className="hidden sm:block">
            <p className="text-[13px] font-medium text-foreground leading-tight">{user?.fullName}</p>
            <p className="text-[12px] text-muted-foreground leading-tight">{user?.username}</p>
          </div>
        </div>
        <ThemeToggle />
        {canPilot(user?.role) && (
          <Button
            variant="ghost"
            size="icon"
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
        <Button variant="ghost" size="icon" onClick={logout} title="Déconnexion">
          <LogOut className="h-4 w-4" />
        </Button>
      </div>
    </header>
  );
}
