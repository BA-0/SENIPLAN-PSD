"use client";

import * as React from "react";
import { cn } from "@/lib/utils";

interface EditableCellProps {
  value: string;
  onChange: (value: string) => void;
  readOnly?: boolean;
  placeholder?: string;
  align?: "left" | "right";
  multiline?: boolean;
  className?: string;
}

export function EditableCell({
  value,
  onChange,
  readOnly,
  placeholder,
  align = "left",
  multiline = false,
  className,
}: EditableCellProps) {
  const baseClass = cn(
    "w-full min-w-[140px] rounded-md border border-border bg-muted/40 px-2.5 py-1.5 text-sm text-foreground shadow-sm transition-colors duration-150",
    "placeholder:text-muted-foreground/70",
    "hover:border-input hover:bg-card",
    "focus:bg-card focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 focus:outline-none",
    "disabled:cursor-default disabled:border-transparent disabled:bg-transparent disabled:shadow-none disabled:text-muted-foreground",
    align === "right" && "text-right tabular-nums",
    className
  );

  if (multiline) {
    return (
      <textarea
        value={value}
        onChange={(e) => onChange(e.target.value)}
        disabled={readOnly}
        placeholder={placeholder}
        rows={2}
        className={cn(baseClass, "resize-y min-h-[40px]")}
      />
    );
  }

  return (
    <input
      type="text"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      disabled={readOnly}
      placeholder={placeholder}
      className={baseClass}
    />
  );
}

interface EditableNumberCellProps {
  value: number;
  onChange: (value: number) => void;
  readOnly?: boolean;
  className?: string;
}

export function EditableNumberCell({ value, onChange, readOnly, className }: EditableNumberCellProps) {
  return (
    <input
      type="number"
      value={Number.isFinite(value) ? value : 0}
      onChange={(e) => onChange(e.target.valueAsNumber || 0)}
      disabled={readOnly}
      className={cn(
        "w-full min-w-[100px] rounded-md border border-border bg-muted/40 px-2.5 py-1.5 text-sm text-foreground text-right tabular-nums shadow-sm transition-colors duration-150",
        "hover:border-input hover:bg-card",
        "focus:bg-card focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 focus:outline-none",
        "disabled:cursor-default disabled:border-transparent disabled:bg-transparent disabled:shadow-none disabled:text-muted-foreground",
        className
      )}
    />
  );
}
