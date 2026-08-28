import { apiClient } from "@/lib/api-client";

async function downloadBlob(url: string, fallbackFilename: string) {
  const response = await apiClient.get(url, { responseType: "blob" });
  const disposition = response.headers["content-disposition"] as string | undefined;
  const match = disposition?.match(/filename="?([^"]+)"?/);
  const filename = match?.[1] ?? fallbackFilename;

  const blobUrl = window.URL.createObjectURL(response.data as Blob);
  const link = document.createElement("a");
  link.href = blobUrl;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  window.URL.revokeObjectURL(blobUrl);
}

export function downloadMyGroupPdf() {
  return downloadBlob("/me/exports/pdf", "plan-strategique.pdf");
}

export function downloadGroupPdf(groupId: number) {
  return downloadBlob(`/admin/exports/groups/${groupId}/pdf`, "plan-strategique.pdf");
}

export function downloadGroupWord(groupId: number) {
  return downloadBlob(`/admin/exports/groups/${groupId}/word`, "plan-strategique.docx");
}

export function downloadConsolidatedExcel(months: number = 4, referenceDate?: string) {
  const params = new URLSearchParams({ months: String(months) });
  if (referenceDate) params.set("referenceDate", referenceDate);
  return downloadBlob(`/admin/exports/consolidated/excel?${params.toString()}`, "plan-strategique-consolide.xlsx");
}

export function downloadConsolidatedPdf() {
  return downloadBlob("/admin/exports/consolidated/pdf", "diagnostic-strategique-consolide.pdf");
}

export function downloadConsolidatedExcelFull() {
  return downloadBlob("/admin/exports/consolidated/excel-complet", "diagnostic-strategique-consolide-complet.xlsx");
}
