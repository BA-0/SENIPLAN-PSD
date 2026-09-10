import type { Role } from "@/types/common";

/**
 * Deux questions distinctes se posent partout dans l'interface, et les confondre est la
 * source d'erreur la plus probable maintenant qu'il existe trois roles :
 *
 *   « qui voit l'espace de pilotage ? »  -> l'admin et la direction generale
 *   « qui peut administrer ? »           -> l'admin seul
 *
 * Le DG consulte tout et arbitre tout, mais ne cree pas de groupe, ne reinitialise pas de
 * mot de passe, ne demarre pas de cycle et ne modifie pas les saisies des directions. Ces
 * memes frontieres sont posees cote serveur dans SecurityConfig : l'interface ne fait que
 * les refleter, elle ne les garantit pas.
 *
 * Une troisieme question s'ajoute depuis la validation a deux niveaux :
 *
 *   « qui approuve ce qui entre dans les documents ? » -> le DG seul
 */

/** Acces a l'espace de pilotage (tableaux de bord, soumissions, documents, projection). */
export function canPilot(role: Role | undefined): boolean {
  return role === "ADMIN" || role === "DIRECTEUR_GENERAL";
}

/** Administration technique : groupes, comptes, cycles, edition directe des saisies. */
export function canAdminister(role: Role | undefined): boolean {
  return role === "ADMIN";
}

/** Validation et revision des sections soumises : premier niveau, comite de pilotage. */
export function canReview(role: Role | undefined): boolean {
  return canPilot(role);
}

/**
 * Second niveau : approuver une section validee, ce qui la fait entrer dans le Document de
 * consolidation, la Note de synthese et le Plan Strategique. Reserve au DG — l'admin ne peut
 * pas se l'accorder a lui-meme, la meme regle etant posee cote serveur.
 */
export function canApproveAsDg(role: Role | undefined): boolean {
  return role === "DIRECTEUR_GENERAL";
}

/** Page d'accueil selon le role, apres connexion ou redirection. */
export function homePathFor(role: Role | undefined): string {
  return canPilot(role) ? "/admin" : "/dashboard";
}

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: "Comité de pilotage",
  DIRECTEUR_GENERAL: "Direction Générale",
  GROUP_LEADER: "Espace chef de groupe",
};
