export function isVoiceSupported(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}

/** Enonce un message via la synthese vocale du navigateur (annule toute annonce en cours). */
export function speak(message: string): void {
  if (!isVoiceSupported()) return;
  window.speechSynthesis.cancel();
  const utterance = new SpeechSynthesisUtterance(message);
  utterance.lang = "fr-FR";
  utterance.rate = 1;
  utterance.pitch = 1;
  window.speechSynthesis.speak(utterance);
}
