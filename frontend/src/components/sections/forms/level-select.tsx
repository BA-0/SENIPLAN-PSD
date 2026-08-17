import { NativeSelect } from "@/components/ui/native-select";
import type { Level } from "@/types/sections";

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
    >
      <option value="">—</option>
      <option value="FORT">Fort</option>
      <option value="MOYEN">Moyen</option>
      <option value="FAIBLE">Faible</option>
    </NativeSelect>
  );
}
