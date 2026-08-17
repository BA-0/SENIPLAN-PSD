"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { GroupProgressDto } from "@/types/api";

export function GroupProgressChart({ groups }: { groups: GroupProgressDto[] }) {
  const data = groups.map((g) => ({ name: g.groupName, percent: g.completionPercent }));

  return (
    <ResponsiveContainer width="100%" height={Math.max(180, data.length * 44)}>
      <BarChart data={data} layout="vertical" margin={{ left: 12, right: 24 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" horizontal={false} />
        <XAxis type="number" domain={[0, 100]} tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }} unit="%" />
        <YAxis type="category" dataKey="name" width={160} tick={{ fontSize: 12, fill: "hsl(var(--muted-foreground))" }} />
        <Tooltip
          contentStyle={{
            borderRadius: 8,
            border: "1px solid hsl(var(--border))",
            fontSize: 13,
            background: "hsl(var(--card))",
            color: "hsl(var(--card-foreground))",
          }}
          formatter={(value: number) => [`${value}%`, "Complétion"]}
        />
        <Bar dataKey="percent" fill="#2D7A45" radius={[0, 6, 6, 0]} barSize={20} />
      </BarChart>
    </ResponsiveContainer>
  );
}
