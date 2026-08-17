import { cn } from "@/lib/utils";
import type { SectionStatus } from "@/types/common";

const DOT_COLORS: Record<SectionStatus, string> = {
  NOT_STARTED: "bg-white/30",
  IN_PROGRESS: "bg-blue-400",
  SUBMITTED: "bg-primary-300",
  VALIDATED: "bg-white",
  REVISION_REQUESTED: "bg-orange-400",
};

export function StatusDot({ status, className }: { status: SectionStatus; className?: string }) {
  return <span className={cn("h-2 w-2 rounded-full shrink-0", DOT_COLORS[status], className)} />;
}
