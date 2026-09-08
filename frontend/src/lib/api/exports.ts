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

/** Note de synthese : le resume de toutes les directions, sans le detail des tableaux. */
export function downloadSynthesisNotePdf() {
  return downloadBlob("/admin/exports/synthesis/pdf", "note-de-synthese-psd-2027-2031.pdf");
}

export function downloadSynthesisNoteWord() {
  return downloadBlob("/admin/exports/synthesis/word", "note-de-synthese-psd-2027-2031.docx");
}

export function downloadPsdFinalPdf() {
  return downloadBlob("/admin/exports/psd-final/pdf", "psd-2027-2031-document-final.pdf");
}

export function downloadPsdFinalWord() {
  return downloadBlob("/admin/exports/psd-final/word", "psd-2027-2031-document-final.docx");
}

export function downloadConsolidatedExcelFull() {
  return downloadBlob("/admin/exports/consolidated/excel-complet", "diagnostic-strategique-consolide-complet.xlsx");
}
