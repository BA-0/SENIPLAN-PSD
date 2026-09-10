export interface NarrativeBlock {
  key: string;
  label: string;
  content: string;
  updatedAt?: string;
  updatedBy?: string;
}

/**
 * Bloc des axes stratégiques de l'entreprise : contenu structuré (JSON), édité par un écran
 * dédié plutôt que dans une zone de texte. Format lu par PsdConsolidatedAxes côté serveur.
 */
export const AXES_CONSOLIDES_KEY = "AXES_CONSOLIDES";

/** Rattachement d'un axe de direction (section S08) à un axe de l'entreprise. */
export interface ConsolidatedAxisLink {
  groupId: number;
  axisCode: string;
}

export interface ConsolidatedAxis {
  title: string;
  objective: string;
  links: ConsolidatedAxisLink[];
}

export interface ConsolidatedAxesContent {
  axes: ConsolidatedAxis[];
}
