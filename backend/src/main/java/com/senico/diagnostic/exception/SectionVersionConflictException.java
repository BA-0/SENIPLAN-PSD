package com.senico.diagnostic.exception;

/**
 * Levee lorsqu'un enregistrement part d'une version de la section plus ancienne que celle en base :
 * la page a ete ouverte avant une autre modification (autre onglet, autre membre de la direction,
 * correction de l'administration). L'accepter effacerait cette modification sans le dire.
 */
public class SectionVersionConflictException extends RuntimeException {

    public static final String CODE = "VERSION_CONFLICT";

    public SectionVersionConflictException() {
        super("Cette section a été modifiée depuis l'ouverture de la page (autre onglet, autre membre de "
                + "la direction ou correction de l'administration). Rechargez la page (F5) pour reprendre "
                + "sur la dernière version.");
    }
}
