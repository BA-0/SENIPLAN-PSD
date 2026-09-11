// Interfaces TypeScript par section (cf. PROMPT_CLAUDE_CODE_DIAGNOSTIC_STRATEGIQUE.md, section 3 & 6).
// Chaque forme correspond exactement au JSON produit/attendu par le backend
// (validation/DefaultSectionContentFactory.java + service/DerivedFieldsService.java).

export type Level = "FORT" | "MOYEN" | "FAIBLE";

// ---- S01 : Analyse des parties prenantes ----
export const STAKEHOLDER_CATEGORIES = ["MAIRIE", "BANQUE", "ETAT", "PRESTATAIRE", "FOURNISSEUR", "AUTRE"] as const;
export type StakeholderCategory = (typeof STAKEHOLDER_CATEGORIES)[number];
export const STAKEHOLDER_CATEGORY_LABELS: Record<StakeholderCategory, string> = {
  MAIRIE: "Mairie",
  BANQUE: "Banque",
  ETAT: "État",
  PRESTATAIRE: "Prestataire",
  FOURNISSEUR: "Fournisseur",
  AUTRE: "Autre",
};

export const STAKEHOLDER_SCOPES = ["INTERNE", "EXTERNE"] as const;
export type StakeholderScope = (typeof STAKEHOLDER_SCOPES)[number];
export const STAKEHOLDER_SCOPE_LABELS: Record<StakeholderScope, string> = {
  INTERNE: "Interne",
  EXTERNE: "Externe",
};

export interface StakeholderRow {
  category: StakeholderCategory | "";
  scope: StakeholderScope | "";
  roles: string;
  expectations: string;
  adaptationStrategy: string;
  importance: Level | "";
  influence: Level | "";
  actions: string;
}
export interface StakeholdersContent {
  rows: StakeholderRow[];
}

// ---- S01B : Analyse des performances de l'annee 2026 ----
export interface PerformanceReview2026Row {
  domain: string;
  indicator: string;
  target2026: number;
  achieved2026: number;
  rate?: number | null; // calcule = realise / cible x 100, null si la cible vaut 0
  comment: string;
}
export interface PerformanceReview2026Content {
  rows: PerformanceReview2026Row[];
}

// ---- S02 : Matrice d'analyse des ressources et competences ----
// Ordre de la matrice transmise par le client (cf. DefaultSectionContentFactory.RESOURCE_KEYS).
export const RESOURCE_KEYS = [
  "CADRE_JURIDIQUE_INSTITUTIONNEL", "LEADERSHIP_PILOTAGE_GOUVERNANCE", "CAPACITES_INSTITUTIONNELLES",
  "BUDGET_RESSOURCES_FINANCIERES", "COMPETENCES", "POSITION_CONCURRENTIELLE",
  "COMPTABILITE_GESTION_FINANCIERE", "SYSTEME_INFORMATION_GESTION", "SUIVI_EVALUATION", "COMMUNICATION",
  "SYSTEME_CONTROLE", "PRODUITS_SERVICES", "CLIENTELE_BENEFICIAIRES", "RECHERCHE_DEVELOPPEMENT",
  "AUTRES_ACHATS_EXPLOITATION_TECHNIQUE_RH",
] as const;
export const RESOURCE_LABELS: Record<string, string> = {
  CADRE_JURIDIQUE_INSTITUTIONNEL: "Cadre juridique, institutionnel et organisationnel",
  LEADERSHIP_PILOTAGE_GOUVERNANCE: "Leadership, Pilotage, Management et Gouvernance",
  POSITION_CONCURRENTIELLE: "Position concurrentielle",
  CAPACITES_INSTITUTIONNELLES: "Capacités institutionnelles (ressources matérielles, financières, humaines et immatérielles)",
  BUDGET_RESSOURCES_FINANCIERES: "Budget ou ressources financières",
  COMPTABILITE_GESTION_FINANCIERE: "Comptabilité et gestion financière",
  SYSTEME_CONTROLE: "Système de contrôle",
  SYSTEME_INFORMATION_GESTION: "Système d'information et de gestion",
  SUIVI_EVALUATION: "Suivi évaluation",
  COMMUNICATION: "Communication",
  AUTRES_ACHATS_EXPLOITATION_TECHNIQUE_RH: "Autres (Achats, Exploitation commerciale, Technique et armement, RH)",
  COMPETENCES: "Compétences",
  PRODUITS_SERVICES:
    "Produits et services à délivrer (portefeuille, qualité, production, marque, tarification, force de vente, compétitivité…)",
  CLIENTELE_BENEFICIAIRES: "Clientèle ou bénéficiaires des prestations (taille, fidélité…)",
  RECHERCHE_DEVELOPPEMENT: "Recherche et développement",
};
export interface ResourceRow {
  resourceKey: string;
  strengths: string;
  weaknesses: string;
  challenges: string;
}
export interface ResourcesMatrixContent {
  rows: ResourceRow[];
}

// ---- S03 : Analyse PESTEL ----
export const PESTEL_AXES = ["POLITIQUE", "ECONOMIQUE", "SOCIAL_CULTUREL", "TECHNOLOGIQUE", "ENVIRONNEMENTAL", "LEGAL"] as const;
export const PESTEL_LABELS: Record<string, string> = {
  POLITIQUE: "Politique",
  ECONOMIQUE: "Économique",
  SOCIAL_CULTUREL: "Social et culturel",
  TECHNOLOGIQUE: "Technologique",
  ENVIRONNEMENTAL: "Environnemental",
  LEGAL: "Légal",
};
export interface PestelRow {
  axis: string;
  threats: string;
  opportunities: string;
  actions: string;
}
export interface PestelContent {
  rows: PestelRow[];
}

// ---- S03B : Synthese de l'analyse des ressources ----
export interface ResourcesSynthesisContent {
  synthesisNote: string;
  majorStrengths: string[];
  majorWeaknesses: string[];
  priorityChallenges: string[];
  resources: ResourceRow[]; // lecture seule, synchronise depuis S02
}

// ---- S04 : Analyse SWOT (FFOM) ----
export interface SwotContent {
  strengths: string[];
  weaknesses: string[];
  opportunities: string[];
  threats: string[];
}

// ---- S05 : Matrice de confrontation SWOT / TOWS ----
export interface TowsActions {
  maximizeStrengths: string;
  minimizeWeaknesses: string;
  strengthsControlWeaknesses: string;
  maximizeOpportunities: string;
  strengthsForOpportunities: string;
  correctWeaknessesViaOpportunities: string;
  minimizeThreats: string;
  strengthsReduceThreats: string;
  minimizeWeaknessesAndThreats: string;
  opportunitiesMinimizeThreats: string;
}
export interface TowsMatrixContent extends TowsActions {
  strengths: string[]; // lecture seule, synchronise depuis S04
  weaknesses: string[]; // lecture seule
  opportunities: string[]; // lecture seule
  threats: string[]; // lecture seule
}

// ---- S06 : Analyse causale ----
export const CAUSAL_SOURCES = ["MANIFESTATION", "CAUSES_IMMEDIATES", "CAUSES_SOUS_JACENTES", "CAUSES_PROFONDES", "SOLUTIONS"] as const;
export const CAUSAL_LABELS: Record<string, string> = {
  MANIFESTATION: "Manifestation des problèmes (effet négatif, besoins)",
  CAUSES_IMMEDIATES: "Causes immédiates",
  CAUSES_SOUS_JACENTES: "Causes sous-jacentes",
  CAUSES_PROFONDES: "Causes profondes",
  SOLUTIONS: "Solutions",
};
export const TOWS_ACTION_LABELS: Record<keyof TowsActions, string> = {
  maximizeStrengths: "Comment maximiser les forces ?",
  minimizeWeaknesses: "Comment minimiser les faiblesses ?",
  strengthsControlWeaknesses: "En quoi les forces permettent-elles de maîtriser les faiblesses ?",
  maximizeOpportunities: "Comment maximiser les opportunités ?",
  strengthsForOpportunities: "Comment utiliser les forces pour tirer parti des opportunités ?",
  correctWeaknessesViaOpportunities: "Comment corriger les faiblesses en tirant parti des opportunités ?",
  minimizeThreats: "Comment minimiser les menaces ?",
  strengthsReduceThreats: "Comment utiliser les forces pour réduire les menaces ?",
  minimizeWeaknessesAndThreats: "Comment minimiser les faiblesses et les menaces ?",
  opportunitiesMinimizeThreats: "En quoi les opportunités permettent-elles de minimiser les menaces ?",
};
export interface CausalRow {
  source: string;
  items: string[];
}
export interface CausalAnalysisContent {
  rows: CausalRow[];
  syncedTowsActions?: TowsActions; // lecture seule, synchronise depuis S05 (actions issues du SWOT)
}

// ---- S06B : Synthese des enjeux, contraintes et defis prioritaires ----
export interface ConstraintsSynthesisRow {
  domain: string;
  constraints: string[];
  challenges: string[];
}
export interface ConstraintsSynthesisContent {
  rows: ConstraintsSynthesisRow[];
}

// ---- S07 : Inventaire (agregation en lecture depuis S01/S03/S04/S06) ----
export interface InventoryContent {
  synthesisNote: string;
  stakeholders: StakeholderRow[];
  pestel: PestelRow[];
  swot: SwotContent;
  causalAnalysis: CausalRow[];
}

// ---- S07B : Cadre strategique (Mission, Valeurs, Vision) ----
export interface StrategicFrameworkContent {
  mission: string[];
  values: string[];
  vision: string;
}

// ---- S08 : Axes strategiques / Orientations ----
export const AXIS_CODES = ["AXE1", "AXE2", "AXE3", "AXE4"] as const;
export interface StrategicAxis {
  axisCode: string;
  title: string;
  objective: string;
  specificObjectives: string[];
}
export interface StrategicAxesContent {
  axes: StrategicAxis[];
}

// ---- S09 : Cadre logique ----
export const LOGFRAME_LEVELS = ["IMPACT", "EFFET", "EFFETS_IMMEDIATS", "EXTRANTS", "RESSOURCES_INTRANTS"] as const;
export const LOGFRAME_LABELS: Record<string, string> = {
  IMPACT: "Impact (Finalité)",
  EFFET: "Effet (Objectif spécifique)",
  EFFETS_IMMEDIATS: "Effets immédiats (Résultats immédiats)",
  EXTRANTS: "Extrants (Produits / Activités)",
  RESSOURCES_INTRANTS: "Ressources / Intrants (Moyens)",
};
export interface LogicalFrameworkRow {
  level: string;
  interventionLogic: string;
  iov: string;
  verificationMeans: string;
  assumptions: string;
}
export interface LogicalFrameworkAxis {
  axisCode: string;
  axisTitle?: string; // lecture seule, synchronise depuis S08
  objective: string;
  rows: LogicalFrameworkRow[];
}
export interface LogicalFrameworkContent {
  axes: LogicalFrameworkAxis[];
}

// ---- S09B : Synthese du cadre logique (lecture seule, reconstruite depuis S09) ----
export interface LogframeSynthesisAxis {
  axisCode: string;
  axisTitle?: string;
  objective: string;
  IMPACT: string;
  EFFET: string;
  EFFETS_IMMEDIATS: string;
  EXTRANTS: string;
  RESSOURCES_INTRANTS: string;
}
export interface LogframeSynthesisContent {
  synthesisNote: string;
  axes: LogframeSynthesisAxis[];
}

// ---- Annees communes (S10, S11, S12, S16) ----
export const PLAN_YEARS = [2027, 2028, 2029, 2030, 2031] as const;

// ---- S10 : Plan d'actions 2027-2031 ----
export interface ActionPlanRow {
  extrant: string;
  activities: string;
  objective: string;
  budget: number;
  years: Record<string, boolean>;
  responsible: string;
}
export interface ActionPlanEffect {
  effectCode: string;
  osCode: string;
  effectLabel: string;
  rows: ActionPlanRow[];
}
export interface ActionPlanAxis {
  axisCode: string;
  axisTitle?: string;
  effects: ActionPlanEffect[];
}
export interface ActionPlanContent {
  axes: ActionPlanAxis[];
  withAmounts: boolean;
}

// ---- S11 : Budget 2027-2031 (FCFA) ----
export interface BudgetRow {
  extrant: string;
  activities: string;
  years: Record<string, number>;
  responsible: string;
  rowTotal?: number; // calcule automatiquement
}
export interface BudgetEffect {
  effectCode: string;
  osCode: string;
  effectLabel: string;
  rows: BudgetRow[];
  yearTotals?: Record<string, number>; // calcule
  effectTotal?: number; // calcule
}
export interface BudgetAxis {
  axisCode: string;
  axisTitle?: string;
  effects: BudgetEffect[];
  axisTotal?: number; // calcule
}
export interface BudgetContent {
  axes: BudgetAxis[];
  withAmounts: boolean;
  grandTotal?: number; // calcule
}

// ---- S12 : Cadre de mesure de rendement 2027-2031 ----
export interface PerformanceRow {
  resultOrExtrant: string;
  indicator: string;
  ref2026: string;
  years: Record<string, string>;
  responsible: string;
}
export interface SyncedEffect {
  osCode: string;
  effectLabel: string;
}
export interface PerformanceGroup {
  level: string;
  rows: PerformanceRow[];
  syncedEffects?: SyncedEffect[]; // lecture seule, synchronise depuis S10 (groupe EFFETS_IMMEDIATS uniquement)
}
export interface PerformanceAxis {
  axisCode: string;
  axisTitle?: string;
  groups: PerformanceGroup[];
}
export interface PerformanceFrameworkContent {
  axes: PerformanceAxis[];
}

// ---- S13 : Fiche d'indicateurs ----
export interface IndicatorRow {
  indicatorTitle: string;
  calculationMethod: string;
  periodicity: string;
  collectionSource: string;
  verificationSource: string;
  responsibleStructure: string;
}
export interface IndicatorSheetContent {
  rows: IndicatorRow[];
}

// ---- S14 : Matrice d'analyse des risques ----
export interface RiskRow {
  category: string;
  present: boolean;
  riskDetails: string;
  levelN: number; // 1..3
  impactAreas: string;
  quotationQ: number; // 1..3
  mitigationActions: string;
  criticality?: number; // calcule = N x Q
  criticalityLabel?: "ELEVEE" | "MOYENNE" | "FAIBLE";
}
export interface RiskMatrixContent {
  rows: RiskRow[];
}

// ---- S14B : Plan d'evolution des effectifs (statut, hierarchie, genre) ----
export const STAFF_CATEGORIES = ["HIERARCHIE", "STATUT"] as const;
export type StaffCategory = (typeof STAFF_CATEGORIES)[number];
export const STAFF_CATEGORY_LABELS: Record<StaffCategory, string> = {
  HIERARCHIE: "Hiérarchie",
  STATUT: "Statut",
};
export const STAFF_LABELS: Record<string, string> = {
  CADRE: "Cadre",
  AGENTS_MAITRISE: "Agents de maîtrise",
  EMPLOYE: "Employé",
  JOURNALIER: "Journalier",
  FONCTIONNAIRE: "Fonctionnaire",
  CDI: "CDI",
  CDD: "CDD",
  EXPATRIE: "Expatrié",
};
export interface StaffCell {
  male: number;
  female: number;
  total?: number; // calcule
}
export interface StaffEvolutionRow {
  category: StaffCategory | string;
  staffKey: string;
  label: string; // intitule libre des lignes ajoutees a la main
  years: Record<string, StaffCell>;
}
export interface StaffEvolutionContent {
  rows: StaffEvolutionRow[];
  totals?: Record<string, StaffCell>; // calcule
}

// ---- S15 : Plan de financement ----
export const FINANCING_SOURCES = ["RESSOURCES_PROPRES", "SUBVENTIONS_PUBLIQUES", "PARTENAIRES_TECHNIQUES_FINANCIERS", "EMPRUNTS", "AUTRES_SOURCES"] as const;
export const FINANCING_LABELS: Record<string, string> = {
  RESSOURCES_PROPRES: "Ressources propres",
  SUBVENTIONS_PUBLIQUES: "Subventions publiques",
  PARTENAIRES_TECHNIQUES_FINANCIERS: "Partenaires techniques et financiers (PTF)",
  EMPRUNTS: "Emprunts",
  AUTRES_SOURCES: "Autres sources",
};
export interface FinancingRow {
  source: string;
  amount: number;
  modalities: string;
  period: string;
  responsible: string;
  percent?: number; // calcule
}
export interface FinancingPlanContent {
  rows: FinancingRow[];
  total?: number; // calcule
}

// ---- S17 : Tableau de synthese du cadre strategique ----
export interface SummaryAction {
  label: string;
  constraintsOrOpportunities: string;
}
export interface SummaryOrientation {
  label: string;
  actions: SummaryAction[];
}
export interface SummaryAxis {
  axisCode: string;
  axisTitle?: string;
  orientations: SummaryOrientation[];
}
export interface StrategicSummaryContent {
  vision: string;
  axes: SummaryAxis[];
}
