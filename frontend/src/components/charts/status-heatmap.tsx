"use client";

import Link from "next/link";
import { cn } from "@/lib/utils";
import type { MatrixCellDto } from "@/types/api";
import type { SectionStatus } from "@/types/common";
import { SECTION_CODES } from "@/types/common";

const CELL_COLOR: Record<SectionStatus, string> = {
  NOT_STARTED: "bg-slate-100",
  IN_PROGRESS: "bg-blue-200",
  SUBMITTED: "bg-primary-300",
  VALIDATED: "bg-primary-500",
  REVISION_REQUESTED: "bg-orange-300",
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
            <th className="sticky left-0 bg-white text-left text-[12px] font-semibold uppercase text-slate-500 px-3 py-2 min-w-[160px]">
              Groupe
            </th>
            {SECTION_CODES.map((code) => (
              <th key={code} className="text-[11px] font-semibold text-slate-500 px-1 py-2 w-9 text-center">
                {code.replace("S", "")}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {groupIds.map((groupId) => (
            <tr key={groupId}>
              <td className="sticky left-0 bg-white text-[13px] text-slate-700 px-3 py-1.5 whitespace-nowrap border-t border-slate-100">
                {groupNames.get(groupId)}
              </td>
              {SECTION_CODES.map((code) => {
                const status = statusFor(groupId, code);
                return (
                  <td key={code} className="p-1 border-t border-slate-100">
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
