"use client";

import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { History } from "lucide-react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { SectionFormRouter } from "@/components/sections/section-form-router";
import { getSectionHistory, getSectionHistoryContent } from "@/lib/api/admin";
import { formatDateTime } from "@/lib/utils";
import type { SectionType } from "@/types/common";

export function VersionHistory({ groupId, code }: { groupId: number; code: string }) {
  const [openVersion, setOpenVersion] = useState<number | null>(null);

  const { data: history, isLoading } = useQuery({
    queryKey: ["admin", "section-history", groupId, code],
    queryFn: () => getSectionHistory(groupId, code),
  });

  const { data: revisionContent, isFetching: isFetchingContent } = useQuery({
    queryKey: ["admin", "section-history-content", groupId, code, openVersion],
    queryFn: () => getSectionHistoryContent(groupId, code, openVersion as number),
    enabled: openVersion !== null,
  });

  if (isLoading || !history || history.length === 0) {
    return null;
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-[15px]">
          <History className="h-4 w-4" /> Historique des versions
        </CardTitle>
      </CardHeader>
      <CardContent className="p-0">
        <div className="divide-y divide-border/60">
          {history.map((rev) => (
            <div key={rev.version} className="flex items-center justify-between px-5 py-3">
              <div className="flex items-center gap-2">
                <span className="text-[13px] font-medium text-foreground">Version {rev.version}</span>
                {rev.current && <Badge variant="submitted">Version actuelle</Badge>}
              </div>
              <div className="flex items-center gap-3">
                <span className="text-[12px] text-muted-foreground">
                  {formatDateTime(rev.createdAt)}
                  {rev.createdByName ? ` · ${rev.createdByName}` : ""}
                </span>
                <Button variant="ghost" size="sm" onClick={() => setOpenVersion(rev.version)}>
                  Consulter
                </Button>
              </div>
            </div>
          ))}
        </div>
      </CardContent>

      <Dialog open={openVersion !== null} onOpenChange={(open) => !open && setOpenVersion(null)}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              {revisionContent ? `${revisionContent.title} — version ${revisionContent.version}` : `Version ${openVersion}`}
            </DialogTitle>
          </DialogHeader>
          {isFetchingContent || !revisionContent ? (
            <div className="h-64 bg-muted rounded-xl animate-pulse" />
          ) : (
            <SectionFormRouter
              type={revisionContent.type as SectionType}
              content={revisionContent.content}
              onChange={() => {}}
              readOnly
            />
          )}
        </DialogContent>
      </Dialog>
    </Card>
  );
}
