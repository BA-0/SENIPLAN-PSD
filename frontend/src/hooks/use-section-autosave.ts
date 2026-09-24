import { useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { saveMySectionDraft } from "@/lib/api/me";
import { extractErrorMessage } from "@/lib/api-client";
import type { SectionContentResponse } from "@/types/common";

/** « unsaved » : une saisie attend son enregistrement automatique (quelques secondes). */
export type SaveStatus = "idle" | "unsaved" | "saving" | "saved" | "error";

const AUTOSAVE_DEBOUNCE_MS = 2500;
const AUTOSAVE_INTERVAL_MS = 20_000;

export function useSectionAutosave<T>(code: string, initial: SectionContentResponse<T> | undefined) {
  const queryClient = useQueryClient();
  const [content, setContent] = useState<T | null>(null);
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [savedAt, setSavedAt] = useState<Date | null>(null);

  const initializedForKey = useRef<string | null>(null);
  // Copie synchrone de la saisie : minuteries et nettoyage lisent la derniere version sans
  // dependre du rendu, et l'intervalle n'est plus recree a chaque frappe.
  const contentRef = useRef<T | null>(null);
  const lastSavedRef = useRef<string>("");
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dirtyRef = useRef(false);

  useEffect(() => {
    // Cle groupe + section : un contenu recu pour un autre groupe ne doit jamais rester affiche.
    const key = initial ? `${initial.groupId}:${code}` : null;
    if (initial && initializedForKey.current !== key) {
      contentRef.current = initial.content;
      setContent(initial.content);
      lastSavedRef.current = JSON.stringify(initial.content);
      initializedForKey.current = key;
      dirtyRef.current = false;
      setStatus("idle");
      setSavedAt(initial.updatedAt ? new Date(initial.updatedAt) : null);
    }
  }, [initial, code]);

  // Le cache de la section recoit ce qui vient d'etre enregistre : en y revenant, on repart de
  // la saisie a jour, et non de la version lue a l'ouverture qu'une sauvegarde suivante aurait
  // remise en base par-dessus.
  const storeSaved = useCallback(
    (response: SectionContentResponse<T>) => {
      queryClient.setQueryData(["me", "section", response.code], response);
      queryClient.invalidateQueries({ queryKey: ["me", "sections", "nav"] });
    },
    [queryClient]
  );

  const { mutate, mutateAsync } = useMutation({
    mutationFn: (payload: T) => saveMySectionDraft<T>(code, payload),
    onMutate: () => setStatus("saving"),
    onSuccess: (response) => {
      lastSavedRef.current = JSON.stringify(response.content);
      dirtyRef.current = false;
      setStatus("saved");
      setSavedAt(new Date());
      storeSaved(response);
    },
    onError: (error) => {
      setStatus("error");
      toast.error(extractErrorMessage(error, "Échec de l'enregistrement automatique"));
    },
  });

  const doSave = useCallback(
    (payload: T) => {
      const serialized = JSON.stringify(payload);
      if (serialized === lastSavedRef.current) return;
      mutate(payload);
    },
    [mutate]
  );

  const update = useCallback(
    (updater: (prev: T) => T) => {
      const prev = contentRef.current;
      if (prev === null) return;
      const next = updater(prev);
      contentRef.current = next;
      // Pas de serialisation du formulaire entier a chaque frappe : doSave compare de toute facon
      // avec le dernier enregistrement avant d'envoyer quoi que ce soit.
      dirtyRef.current = true;
      setContent(next);
      setStatus((s) => (s === "saving" ? s : "unsaved"));
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
      debounceTimer.current = setTimeout(() => {
        debounceTimer.current = null;
        doSave(next);
      }, AUTOSAVE_DEBOUNCE_MS);
    },
    [doSave]
  );


  // Avant une soumission : le serveur valide ce qu'il a en base, pas ce qui est a l'ecran. Une
  // frappe de moins de 2,5 s serait sinon soumise sans elle, puis refusee une fois la section
  // verrouillee.
  const flush = useCallback(async () => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    debounceTimer.current = null;
    const pending = contentRef.current;
    if (pending === null || JSON.stringify(pending) === lastSavedRef.current) return;
    await mutateAsync(pending);
  }, [mutateAsync]);

  // Le bouton « Enregistrer » confirme toujours : sans message, un clic sur une section deja
  // enregistree automatiquement laissait croire que rien ne s'etait passe.
  const saveNow = useCallback(async () => {
    try {
      await flush();
      toast.success("Section enregistrée");
    } catch {
      // L'echec est deja signale par onError.
    }
  }, [flush]);

  // Fermer l'onglet pendant qu'une saisie attend son enregistrement la perdrait : le navigateur
  // demande confirmation.
  useEffect(() => {
    const avertir = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current) return;
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", avertir);
    return () => window.removeEventListener("beforeunload", avertir);
  }, []);

  useEffect(() => {
    const interval = setInterval(() => {
      if (dirtyRef.current && contentRef.current !== null) doSave(contentRef.current);
    }, AUTOSAVE_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [doSave]);

  // Passer a une autre section moins de 2,5 s apres une frappe annulait l'enregistrement differe :
  // la saisie en attente etait perdue. Elle part desormais aussitot.
  useEffect(() => {
    return () => {
      if (!debounceTimer.current) return;
      clearTimeout(debounceTimer.current);
      debounceTimer.current = null;
      const pending = contentRef.current;
      if (pending === null || JSON.stringify(pending) === lastSavedRef.current) return;
      saveMySectionDraft<T>(code, pending)
        .then(storeSaved)
        .catch((error) => toast.error(extractErrorMessage(error, "Échec de l'enregistrement automatique")));
    };
  }, [code, storeSaved]);

  return { content, update, status, savedAt, saveNow, flush };
}
