import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";

import { listGroupSections } from "@/lib/api/admin";
import { listGroups } from "@/lib/api/groups";

/**
 * Intitules officiels des sections du canevas, indexes par code. Les codes (S01, S09B...) restent
 * internes : les ecrans d'administration affichent l'intitule. Le referentiel est lu via un groupe
 * existant (memes cles de cache que la consolidation en direct).
 */
export function useSectionTitles(): ReadonlyMap<string, string> {
  const { data: groups } = useQuery({ queryKey: ["admin", "groups"], queryFn: listGroups });
  const firstGroupId = groups?.[0]?.id;
  const { data: sections } = useQuery({
    queryKey: ["admin", "groups", firstGroupId, "sections"],
    queryFn: () => listGroupSections(firstGroupId as number),
    enabled: !!firstGroupId,
  });
  return useMemo(() => {
    const map = new Map<string, string>();
    (sections ?? []).forEach((s) => map.set(s.code, s.title));
    return map;
  }, [sections]);
}
