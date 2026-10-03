/**
 * Blocs de la note de synthèse, tels que le serveur les produit (cf. ExportBlock côté serveur,
 * champ `type`). La Direction Générale les corrige puis les renvoie tels quels : les champs
 * qu'elle ne touche pas (couleurs, alignements, fusions) doivent revenir intacts.
 */

export type Background =
  | "NONE"
  | "RED"
  | "ORANGE"
  | "BLUE"
  | "GREEN"
  | "GREY"
  | "PRIMARY_LIGHT"
  | "VIOLET"
  | "PRIMARY_DARK";

export interface Attribution {
  text: string;
  colorHexes: string[];
}

export interface Cell {
  text: string;
  bold: boolean;
  align: "LEFT" | "CENTER" | "RIGHT";
  background: Background;
  attributions: Attribution[];
  /** 0 : place tenue par une cellule fusionnée d'une ligne supérieure. */
  rowSpan: number;
}

export interface TableRow {
  cells: Cell[];
  emphasized: boolean;
  rowBackground: Background;
  /** Intertitre sur toute la largeur du tableau : seule la première cellule est rendue. */
  band: boolean;
}

export interface HeaderBand {
  label: string;
  span: number;
}

export interface QuadrantCell {
  title: string;
  items: string[];
}

export interface AttributedQuadrantCell {
  title: string;
  caption: string | null;
  items: Attribution[];
}

export type NoteBlock =
  | { type: "HEADING"; text: string; level: number }
  | { type: "PARAGRAPH"; text: string; italic: boolean; bold: boolean }
  | { type: "KEY_VALUE_LIST"; title: string | null; pairs: { label: string; value: string }[]; boxed: boolean }
  | { type: "BULLET_LIST"; title: string | null; items: string[] }
  | { type: "ATTRIBUTED_LIST"; title: string | null; items: Attribution[] }
  | { type: "ATTRIBUTED_QUADRANT"; cells: AttributedQuadrantCell[]; tints: Background[] }
  | { type: "COLOR_LEGEND"; title: string | null; entries: Attribution[] }
  | { type: "TABLE"; columnHeaders: string[]; rows: TableRow[]; widths: number[]; bands: HeaderBand[] }
  | { type: "CALLOUT"; text: string; tone: "ANALYSIS" | "WARNING" }
  | { type: "PLACEHOLDER"; label: string }
  | { type: "PROSE"; paragraphs: string[] }
  | { type: "METRIC_GRID"; metrics: { label: string; value: string }[] }
  | { type: "QUADRANT"; cells: QuadrantCell[] }
  | { type: "CHART"; kind: string; title: string; subtitle: string | null; categories: string[]; series: unknown[] };

export interface SynthesisNote {
  blocks: NoteBlock[];
  /** Vrai si la Direction Générale a enregistré une version corrigée. */
  edited: boolean;
  /** Vrai si le document généré a changé depuis la correction : elle ne reprend pas ces changements. */
  sourceChanged: boolean;
  updatedAt?: string | null;
  updatedBy?: string | null;
  /** Empreinte de `blocks` : le poste la renvoie avec ses corrections, pour la fusion. */
  version: string;
  /** À l'enregistrement : éléments modifiés aussi par un autre poste, où cette version l'a emporté. */
  conflicts: number;
}
