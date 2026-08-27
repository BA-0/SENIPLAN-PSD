import { cn } from "@/lib/utils";
import { NativeSelect } from "@/components/ui/native-select";
import type { Level } from "@/types/sections";

const LEVEL_COLORS: Record<Level, string> = {
  FORT: "text-accent-700 dark:text-accent-300",
  MOYEN: "text-blue-700 dark:text-blue-300",
  FAIBLE: "text-primary-700 dark:text-primary-300",
};

export function LevelSelect({
  value,
  onChange,
  readOnly,
}: {
  value: Level | "";
  onChange: (value: Level | "") => void;
  readOnly?: boolean;
}) {
  return (
    <NativeSelect
      cellStyle
      value={value}
      disabled={readOnly}
      onChange={(e) => onChange(e.target.value as Level | "")}
      className={cn("font-medium", value && LEVEL_COLORS[value])}
    >
      <option value="">—</option>
      <option value="FORT">Fort</option>
      <option value="MOYEN">Moyen</option>
      <option value="FAIBLE">Faible</option>
    </NativeSelect>
  );
}
