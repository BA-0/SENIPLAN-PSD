import { SECTION_CODES } from "@/types/common";
import type { SectionStatus } from "@/types/common";

/**
 * Regroupement des 23 sections du canevas en 5 parties (et sous-parties pour le
 * diagnostic, la plus longue). Sert a raccourcir les listes plates de sections :
 * navigation laterale et checklist du tableau de bord.
 *
 * Les parties suivent exactement l'ordre d'affichage valide par le client
 * (migration V10__client_review_sections_and_order.sql) : chacune est un bloc
 * contigu de cet ordre, si bien que grouper ne reordonne jamais les sections.
 * Toute nouvelle section du canevas doit etre ajoutee ici, faute de quoi elle
 * atterrit dans la partie "Autres sections" en fin de liste.
 */
export type SectionCode = (typeof SECTION_CODES)[number];

export interface SectionSubPart {
  /** null quand la partie n'a pas de subdivision interne. */
  title: string | null;
  codes: readonly SectionCode[];
}

export interface SectionPart {
  id: string;
  /** Numero romain affiche devant l'intitule ("I", "II", ...). */
  numeral: string;
  title: string;
  subParts: readonly SectionSubPart[];
}

export const SECTION_PARTS = [
  {
    id: "diagnostic",
    numeral: "I",
    title: "Diagnostic stratégique",
    subParts: [
      { title: "État des lieux", codes: ["S01", "S01B"] },
      { title: "Analyse interne et externe", codes: ["S02", "S03", "S03B"] },
      { title: "Synthèse du diagnostic", codes: ["S04", "S05", "S06", "S06B", "S07"] },
    ],
  },
  {
    id: "cadre-strategique",
    numeral: "II",
    title: "Cadre stratégique",
    subParts: [{ title: null, codes: ["S07B", "S08"] }],
  },
  {
    id: "programmation",
    numeral: "III",
    title: "Programmation et budgétisation",
    subParts: [{ title: null, codes: ["S09", "S09B", "S11", "S10"] }],
  },
  {
    id: "suivi-evaluation",
    numeral: "IV",
    title: "Suivi, évaluation et risques",
    subParts: [{ title: null, codes: ["S13", "S12", "S14"] }],
  },
  {
    id: "moyens-financement",
    numeral: "V",
    title: "Moyens, financement et synthèse",
    subParts: [{ title: null, codes: ["S14B", "S15", "S17"] }],
  },
] as const satisfies readonly SectionPart[];

/**
 * Garde-fou de compilation : ajouter une section a SECTION_CODES sans la ranger
 * dans une partie casse le build ici, plutot que de la faire glisser en silence
 * dans "Autres sections".
 */
type CoveredCode = (typeof SECTION_PARTS)[number]["subParts"][number]["codes"][number];
type UnassignedCode = Exclude<SectionCode, CoveredCode>;
const _everySectionIsAssigned: [UnassignedCode] extends [never]
  ? true
  : { "sections absentes de SECTION_PARTS": UnassignedCode } = true;
void _everySectionIsAssigned;

/** Filet de securite : accueille les codes absents de SECTION_PARTS. */
const OTHER_PART: SectionPart = {
  id: "autres",
  numeral: "",
  title: "Autres sections",
  subParts: [{ title: null, codes: [] }],
};

const PART_BY_CODE = new Map<string, { part: SectionPart; subPartIndex: number }>();
for (const part of SECTION_PARTS) {
  part.subParts.forEach((subPart, subPartIndex) => {
    for (const code of subPart.codes) {
      PART_BY_CODE.set(code, { part, subPartIndex });
    }
  });
}

/** Tous les codes d'une partie, sous-parties confondues, dans l'ordre du canevas. */
export function partCodes(part: SectionPart): readonly SectionCode[] {
  return part.subParts.flatMap((subPart) => subPart.codes);
}

export function findPartForCode(code: string): SectionPart | null {
  return PART_BY_CODE.get(code)?.part ?? null;
}

export interface GroupedSections<T> {
  part: SectionPart;
  /** Toutes les sections de la partie, a plat (navigation laterale). */
  sections: T[];
  /** Les memes, ventilees par sous-partie ; une seule entree de titre null si la partie n'en a pas. */
  subGroups: { title: string | null; sections: T[] }[];
}

/**
 * Ventile des sections par partie en preservant l'ordre d'entree a l'interieur de
 * chaque groupe. Les parties sans aucune section sont omises : un groupe de travail
 * ne se voit attribuer qu'un sous-ensemble du canevas.
 */
export function groupSectionsByPart<T extends { code: string }>(sections: readonly T[]): GroupedSections<T>[] {
  const buckets = new Map<string, GroupedSections<T>>();

  const bucketFor = (part: SectionPart) => {
    let bucket = buckets.get(part.id);
    if (!bucket) {
      bucket = {
        part,
        sections: [],
        subGroups: part.subParts.map((subPart) => ({ title: subPart.title, sections: [] })),
      };
      buckets.set(part.id, bucket);
    }
    return bucket;
  };

  for (const section of sections) {
    const placement = PART_BY_CODE.get(section.code);
    const bucket = bucketFor(placement?.part ?? OTHER_PART);
    bucket.sections.push(section);
    bucket.subGroups[placement?.subPartIndex ?? 0].sections.push(section);
  }

  const ordered = [...SECTION_PARTS, OTHER_PART]
    .map((part) => buckets.get(part.id))
    .filter((bucket): bucket is GroupedSections<T> => bucket !== undefined);

  for (const bucket of ordered) {
    bucket.subGroups = bucket.subGroups.filter((subGroup) => subGroup.sections.length > 0);
  }
  return ordered;
}

/** Avancement d'une partie repliee, pour ne pas avoir a la deplier pour le connaitre. */
export function countValidated(sections: readonly { status: SectionStatus }[]): number {
  return sections.filter((s) => s.status === "VALIDATED").length;
}
