"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { GroupProgressDto } from "@/types/api";

export function GroupProgressChart({ groups }: { groups: GroupProgressDto[] }) {
  const data = groups.map((g) => ({ name: g.groupName, percent: g.completionPercent }));

  return (
    <ResponsiveContainer width="100%" height={Math.max(180, data.length * 44)}>
      <BarChart data={data} layout="vertical" margin={{ left: 12, right: 24 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#F1F5F9" horizontal={false} />
        <XAxis type="number" domain={[0, 100]} tick={{ fontSize: 12, fill: "#64748B" }} unit="%" />
        <YAxis type="category" dataKey="name" width={160} tick={{ fontSize: 12, fill: "#64748B" }} />
        <Tooltip
          contentStyle={{ borderRadius: 8, border: "1px solid #E2E8F0", fontSize: 13 }}
          formatter={(value: number) => [`${value}%`, "Complétion"]}
        />
        <Bar dataKey="percent" fill="#2D7A45" radius={[0, 6, 6, 0]} barSize={20} />
      </BarChart>
    </ResponsiveContainer>
  );
}
