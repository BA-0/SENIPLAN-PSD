package com.senico.diagnostic.exception;

/**
 * Levee lorsqu'on tente de modifier une section SUBMITTED ou VALIDATED
 * (verrouillage cote backend, cf. exigence securite).
 */
public class SectionLockedException extends RuntimeException {
    public SectionLockedException(String message) {
        super(message);
    }
}
