"use client";

import { LogOut, Volume2, VolumeX } from "lucide-react";
import { Button } from "@/components/ui/button";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { useCurrentUser } from "@/hooks/use-current-user";
import { useRealtimeGroup } from "@/hooks/use-realtime-group";
import { useVoiceNotificationsStore } from "@/store/voice-notifications-store";

export function Header() {
  const { user, logout } = useCurrentUser();
  const voiceEnabled = useVoiceNotificationsStore((s) => s.enabled);
  const toggleVoice = useVoiceNotificationsStore((s) => s.toggle);

  useRealtimeGroup(user?.role === "GROUP_LEADER" ? user.groupId : null);

  const initials = (user?.fullName ?? "")
    .split(" ")
    .map((p) => p[0])
    .slice(0, 2)
    .join("")
    .toUpperCase();

  return (
    <header className="h-16 shrink-0 bg-card border-b border-border flex items-center justify-between px-6 sticky top-0 z-10">
      <div>
        <p className="text-sm font-medium text-foreground">{user?.groupName ?? "Administration"}</p>
        <p className="text-[12px] text-muted-foreground">
          {user?.role === "ADMIN" ? "Comité de pilotage" : "Espace chef de groupe"}
        </p>
      </div>

      <div className="flex items-center gap-3">
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
        <Button
          variant="ghost"
          size="icon"
          onClick={toggleVoice}
          title={voiceEnabled ? "Désactiver les notifications vocales" : "Activer les notifications vocales"}
        >
          {voiceEnabled ? <Volume2 className="h-4 w-4" /> : <VolumeX className="h-4 w-4" />}
        </Button>
        <Button variant="ghost" size="icon" onClick={logout} title="Déconnexion">
          <LogOut className="h-4 w-4" />
        </Button>
      </div>
    </header>
  );
}
