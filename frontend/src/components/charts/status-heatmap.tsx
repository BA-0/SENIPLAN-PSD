"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import type { MatrixCellDto } from "@/types/api";
import type { SectionStatus } from "@/types/common";
import { SECTION_CODES } from "@/types/common";

const CELL_COLOR: Record<SectionStatus, string> = {
  NOT_STARTED: "bg-slate-100 dark:bg-white/10",
  IN_PROGRESS: "bg-blue-200 dark:bg-blue-500/50",
  SUBMITTED: "bg-primary-300 dark:bg-primary-500/70",
  VALIDATED: "bg-primary-500",
  REVISION_REQUESTED: "bg-orange-300 dark:bg-orange-500/60",
};

export function StatusHeatmap({ cells }: { cells: MatrixCellDto[] }) {
  const groupIds = Array.from(new Set(cells.map((c) => c.groupId)));
  const groupNames = new Map(cells.map((c) => [c.groupId, c.groupName]));

  function statusFor(groupId: number, sectionCode: string): SectionStatus {
    return (cells.find((c) => c.groupId === groupId && c.sectionCode === sectionCode)?.status ?? "NOT_STARTED") as SectionStatus;
  }

  return (
    <div className="overflow-x-auto scrollbar-thin">
      <table className="border-collapse">
        <thead>
          <tr>
            <th className="sticky left-0 bg-card text-left text-[12px] font-semibold uppercase text-muted-foreground px-3 py-2 min-w-[160px]">
              Groupe
            </th>
            {SECTION_CODES.map((code) => (
              <th key={code} className="text-[11px] font-semibold text-muted-foreground px-1 py-2 w-9 text-center">
                {code.replace("S", "")}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {groupIds.map((groupId) => (
            <tr key={groupId}>
              <td className="sticky left-0 bg-card text-[13px] text-foreground px-3 py-1.5 whitespace-nowrap border-t border-border">
                {groupNames.get(groupId)}
              </td>
              {SECTION_CODES.map((code) => {
                const status = statusFor(groupId, code);
                return (
                  <td key={code} className="p-1 border-t border-border">
                    <Link
                      href={`/admin/groups/${groupId}/sections/${code}`}
                      title={`${code} — ${status}`}
                      className={cn("block h-7 w-7 rounded-md mx-auto transition-transform hover:scale-110", CELL_COLOR[status])}
                    />
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
