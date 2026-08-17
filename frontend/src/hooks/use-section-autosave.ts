import { useCallback, useEffect, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { saveMySectionDraft } from "@/lib/api/me";
import { extractErrorMessage } from "@/lib/api-client";
import type { SectionContentResponse } from "@/types/common";

export type SaveStatus = "idle" | "saving" | "saved" | "error";

const AUTOSAVE_DEBOUNCE_MS = 2500;
const AUTOSAVE_INTERVAL_MS = 20_000;

export function useSectionAutosave<T>(code: string, initial: SectionContentResponse<T> | undefined) {
  const queryClient = useQueryClient();
  const [content, setContent] = useState<T | null>(null);
  const [status, setStatus] = useState<SaveStatus>("idle");
  const [savedAt, setSavedAt] = useState<Date | null>(null);

  const initializedForCode = useRef<string | null>(null);
  const lastSavedRef = useRef<string>("");
  const debounceTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const dirtyRef = useRef(false);

  useEffect(() => {
    if (initial && initializedForCode.current !== code) {
      setContent(initial.content);
      lastSavedRef.current = JSON.stringify(initial.content);
      initializedForCode.current = code;
      dirtyRef.current = false;
      setStatus("idle");
      setSavedAt(initial.updatedAt ? new Date(initial.updatedAt) : null);
    }
  }, [initial, code]);

  const mutation = useMutation({
    mutationFn: (payload: T) => saveMySectionDraft<T>(code, payload),
    onMutate: () => setStatus("saving"),
    onSuccess: (response) => {
      lastSavedRef.current = JSON.stringify(response.content);
      dirtyRef.current = false;
      setStatus("saved");
      setSavedAt(new Date());
      queryClient.invalidateQueries({ queryKey: ["me", "sections", "nav"] });
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
      mutation.mutate(payload);
    },
    [mutation]
  );

  const update = useCallback(
    (updater: (prev: T) => T) => {
      setContent((prev) => {
        if (prev === null) return prev;
        const next = updater(prev);
        dirtyRef.current = JSON.stringify(next) !== lastSavedRef.current;
        if (debounceTimer.current) clearTimeout(debounceTimer.current);
        debounceTimer.current = setTimeout(() => doSave(next), AUTOSAVE_DEBOUNCE_MS);
        return next;
      });
    },
    [doSave]
  );

  const saveNow = useCallback(() => {
    if (debounceTimer.current) clearTimeout(debounceTimer.current);
    if (content !== null) doSave(content);
  }, [content, doSave]);

  useEffect(() => {
    const interval = setInterval(() => {
      if (dirtyRef.current && content !== null) doSave(content);
    }, AUTOSAVE_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [content, doSave]);

  useEffect(() => {
    return () => {
      if (debounceTimer.current) clearTimeout(debounceTimer.current);
    };
  }, []);

  return { content, update, status, savedAt, saveNow };
}
