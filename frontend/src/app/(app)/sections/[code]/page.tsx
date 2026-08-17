"use client";

import { useCallback } from "react";
import { useParams } from "next/navigation";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";

import { getMySectionContent, listMySections, submitMySection } from "@/lib/api/me";
import { extractErrorMessage } from "@/lib/api-client";
import { getSubmitBlockedReason } from "@/lib/section-submission";
import { useSectionAutosave } from "@/hooks/use-section-autosave";
import { usePresencePublisher } from "@/hooks/use-presence-publisher";
import { SectionShell } from "@/components/sections/section-shell";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import type { SectionType } from "@/types/common";

export default function SectionFormPage() {
  const params = useParams<{ code: string }>();
  const code = params.code;
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["me", "section", code],
    queryFn: () => getMySectionContent(code),
  });

  const { data: navSections } = useQuery({
    queryKey: ["me", "sections", "nav"],
    queryFn: listMySections,
  });

  const { content, update, status, savedAt, saveNow } = useSectionAutosave(code, data);

  const { notifyTyping } = usePresencePublisher({
    groupId: data?.groupId ?? null,
    groupName: data?.groupName ?? null,
    sectionId: data?.sectionId ?? null,
    sectionCode: data?.code ?? null,
  });

  const handleChange = useCallback(
    (updater: Parameters<typeof update>[0]) => {
      notifyTyping();
      update(updater);
    },
    [notifyTyping, update]
  );

  const submitMutation = useMutation({
    mutationFn: () => submitMySection(code),
    onSuccess: () => {
      toast.success("Section soumise avec succès");
      queryClient.invalidateQueries({ queryKey: ["me", "section", code] });
      queryClient.invalidateQueries({ queryKey: ["me", "sections", "nav"] });
      queryClient.invalidateQueries({ queryKey: ["me", "dashboard"] });
    },
    onError: (error) => toast.error(extractErrorMessage(error, "Échec de la soumission")),
  });

  if (isLoading || !data || content === null) {
    return (
      <div className="space-y-4 animate-pulse">
        <div className="h-8 w-96 bg-slate-200 rounded" />
        <div className="h-96 bg-slate-200 rounded-xl" />
      </div>
    );
  }

  const sortedNav = (navSections ?? []).slice().sort((a, b) => a.order - b.order);
  const currentIndex = sortedNav.findIndex((s) => s.code === code);
  const prevSection = currentIndex > 0 ? sortedNav[currentIndex - 1] : null;
  const nextSection = currentIndex >= 0 && currentIndex < sortedNav.length - 1 ? sortedNav[currentIndex + 1] : null;

  return (
    <SectionShell
      code={data.code}
      title={data.title}
      status={data.status}
      locked={data.locked}
      adminComment={data.adminComment}
      saveStatus={status}
      savedAt={savedAt}
      onSaveNow={saveNow}
      onSubmit={() => submitMutation.mutate()}
      submitting={submitMutation.isPending}
      submitBlockedReason={getSubmitBlockedReason(data.type as SectionType, content)}
      prevSection={prevSection}
      nextSection={nextSection}
    >
      <SectionFormRouter
        type={data.type as SectionType}
        content={content}
        onChange={handleChange}
        readOnly={data.locked}
      />
    </SectionShell>
  );
}
