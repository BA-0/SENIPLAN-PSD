"use client";

import { StakeholdersForm } from "./forms/stakeholders-form";
import { PerformanceReview2026Form } from "./forms/performance-review-2026-form";
import { ResourcesMatrixForm } from "./forms/resources-matrix-form";
import { PestelForm } from "./forms/pestel-form";
import { ResourcesSynthesisForm } from "./forms/resources-synthesis-form";
import { SwotForm } from "./forms/swot-form";
import { TowsMatrixForm } from "./forms/tows-matrix-form";
import { CausalAnalysisForm } from "./forms/causal-analysis-form";
import { ConstraintsSynthesisForm } from "./forms/constraints-synthesis-form";
import { InventoryForm } from "./forms/inventory-form";
import { StrategicFrameworkForm } from "./forms/strategic-framework-form";
import { StrategicAxesForm } from "./forms/strategic-axes-form";
import { LogicalFrameworkForm } from "./forms/logical-framework-form";
import { LogframeSynthesisForm } from "./forms/logframe-synthesis-form";
import { ActionPlanForm } from "./forms/action-plan-form";
import { BudgetForm } from "./forms/budget-form";
import { PerformanceFrameworkForm } from "./forms/performance-framework-form";
import { IndicatorSheetForm } from "./forms/indicator-sheet-form";
import { RiskMatrixForm } from "./forms/risk-matrix-form";
import { StaffEvolutionForm } from "./forms/staff-evolution-form";
import { FinancingPlanForm } from "./forms/financing-plan-form";
import { StrategicSummaryForm } from "./forms/strategic-summary-form";
import type { SectionType } from "@/types/common";

interface SectionFormRouterProps {
  type: SectionType;
  content: unknown;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  onChange: (updater: (prev: any) => any) => void;
  readOnly: boolean;
}

/* eslint-disable @typescript-eslint/no-explicit-any */
export function SectionFormRouter({ type, content, onChange, readOnly }: SectionFormRouterProps) {
  const props = { content: content as any, onChange: onChange as any, readOnly };

  switch (type) {
    case "STAKEHOLDERS":
      return <StakeholdersForm {...props} />;
    case "PERFORMANCE_REVIEW_2026":
      return <PerformanceReview2026Form {...props} />;
    case "RESOURCES_MATRIX":
      return <ResourcesMatrixForm {...props} />;
    case "PESTEL":
      return <PestelForm {...props} />;
    case "RESOURCES_SYNTHESIS":
      return <ResourcesSynthesisForm {...props} />;
    case "SWOT":
      return <SwotForm {...props} />;
    case "TOWS_MATRIX":
      return <TowsMatrixForm {...props} />;
    case "CAUSAL_ANALYSIS":
      return <CausalAnalysisForm {...props} />;
    case "CONSTRAINTS_SYNTHESIS":
      return <ConstraintsSynthesisForm {...props} />;
    case "INVENTORY":
      return <InventoryForm {...props} />;
    case "STRATEGIC_FRAMEWORK":
      return <StrategicFrameworkForm {...props} />;
    case "STRATEGIC_AXES":
      return <StrategicAxesForm {...props} />;
    case "LOGICAL_FRAMEWORK":
      return <LogicalFrameworkForm {...props} />;
    case "LOGFRAME_SYNTHESIS":
      return <LogframeSynthesisForm {...props} />;
    case "ACTION_PLAN":
      return <ActionPlanForm {...props} />;
    case "BUDGET":
      return <BudgetForm {...props} />;
    case "PERFORMANCE_FRAMEWORK":
      return <PerformanceFrameworkForm {...props} />;
    case "INDICATOR_SHEET":
      return <IndicatorSheetForm {...props} />;
    case "RISK_MATRIX":
      return <RiskMatrixForm {...props} />;
    case "STAFF_EVOLUTION":
      return <StaffEvolutionForm {...props} />;
    case "FINANCING_PLAN":
      return <FinancingPlanForm {...props} />;
    case "STRATEGIC_SUMMARY":
      return <StrategicSummaryForm {...props} />;
    default:
      return <p className="text-muted-foreground">Type de section non pris en charge : {type}</p>;
  }
}
/* eslint-enable @typescript-eslint/no-explicit-any */
