"use client";

import { StakeholdersForm } from "./forms/stakeholders-form";
import { ResourcesMatrixForm } from "./forms/resources-matrix-form";
import { PestelForm } from "./forms/pestel-form";
import { SwotForm } from "./forms/swot-form";
import { TowsMatrixForm } from "./forms/tows-matrix-form";
import { CausalAnalysisForm } from "./forms/causal-analysis-form";
import { InventoryForm } from "./forms/inventory-form";
import { StrategicAxesForm } from "./forms/strategic-axes-form";
import { LogicalFrameworkForm } from "./forms/logical-framework-form";
import { ActionPlanForm } from "./forms/action-plan-form";
import { BudgetForm } from "./forms/budget-form";
import { PerformanceFrameworkForm } from "./forms/performance-framework-form";
import { IndicatorSheetForm } from "./forms/indicator-sheet-form";
import { RiskMatrixForm } from "./forms/risk-matrix-form";
import { FinancingPlanForm } from "./forms/financing-plan-form";
import { BusinessPlanForm } from "./forms/business-plan-form";
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
    case "RESOURCES_MATRIX":
      return <ResourcesMatrixForm {...props} />;
    case "PESTEL":
      return <PestelForm {...props} />;
    case "SWOT":
      return <SwotForm {...props} />;
    case "TOWS_MATRIX":
      return <TowsMatrixForm {...props} />;
    case "CAUSAL_ANALYSIS":
      return <CausalAnalysisForm {...props} />;
    case "INVENTORY":
      return <InventoryForm {...props} />;
    case "STRATEGIC_AXES":
      return <StrategicAxesForm {...props} />;
    case "LOGICAL_FRAMEWORK":
      return <LogicalFrameworkForm {...props} />;
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
    case "FINANCING_PLAN":
      return <FinancingPlanForm {...props} />;
    case "BUSINESS_PLAN":
      return <BusinessPlanForm {...props} />;
    case "STRATEGIC_SUMMARY":
      return <StrategicSummaryForm {...props} />;
    default:
      return <p className="text-slate-500">Type de section non pris en charge : {type}</p>;
  }
}
/* eslint-enable @typescript-eslint/no-explicit-any */
