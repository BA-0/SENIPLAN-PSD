/**
 * Mot de passe remis par l'admin, saisi a la connexion et repris tel quel par l'ecran de
 * changement impose. Garde en memoire seulement (jamais en storage) : un rechargement de la
 * page le perd, et le champ redevient alors a saisir.
 */
let received: string | null = null;

export function rememberReceivedPassword(password: string) {
  received = password;
}

export function peekReceivedPassword(): string | null {
  return received;
}

export function clearReceivedPassword() {
  received = null;
}
