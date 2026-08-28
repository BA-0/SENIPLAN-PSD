export function isVoiceSupported(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}

/**
 * File d'attente maison pour les annonces vocales : on ne s'appuie pas sur la file native de
 * `speechSynthesis`, connue pour se bloquer silencieusement (bug Chrome de longue date, aggrave
 * par les onglets restes inactifs comme un ecran de projection) — un message coince y bloquerait
 * indefiniment toutes les annonces suivantes sans aucune erreur visible.
 */
const queue: string[] = [];
let speaking = false;
let watchdog: ReturnType<typeof setTimeout> | null = null;
let keepAlive: ReturnType<typeof setInterval> | null = null;

const MAX_QUEUE_LENGTH = 8;
const WATCHDOG_MS = 12_000;

/** Annonce un message via la synthese vocale du navigateur (mis en file, sans interrompre une annonce en cours). */
export function speak(message: string): void {
  if (!isVoiceSupported()) return;
  queue.push(message);
  if (queue.length > MAX_QUEUE_LENGTH) queue.shift();
  processQueue();
}

function processQueue(): void {
  if (speaking) return;
  const message = queue.shift();
  if (!message) return;

  speaking = true;
  const utterance = new SpeechSynthesisUtterance(message);
  utterance.lang = "fr-FR";
  utterance.rate = 1;
  utterance.pitch = 1;
  utterance.onend = advance;
  utterance.onerror = advance;

  // Contourne le bug Chrome qui met la synthese vocale en pause apres ~15s d'inactivite du systeme.
  keepAlive = setInterval(() => window.speechSynthesis.resume(), 5_000);
  // Filet de securite : si l'evenement de fin n'arrive jamais (file native bloquee), on force la suite.
  watchdog = setTimeout(advance, WATCHDOG_MS);

  window.speechSynthesis.speak(utterance);
}

function advance(): void {
  if (watchdog) clearTimeout(watchdog);
  if (keepAlive) clearInterval(keepAlive);
  watchdog = null;
  keepAlive = null;
  speaking = false;
  processQueue();
}
