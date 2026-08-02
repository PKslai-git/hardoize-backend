package com.digneequipe.hardoize.util;

/**
 * Compare deux numéros de téléphone en ignorant indicatif pays,
 * espaces et tirets — comparer sur les 8 derniers chiffres suffit à
 * identifier la même ligne. Centralisé ici : c'était dupliqué à
 * l'identique dans GroupeService et MultiModeService.
 */
public final class TelephoneUtil {

    private TelephoneUtil() {}

    public static boolean equivalents(String a, String b) {
        if (a == null || b == null) return false;
        String na = a.replaceAll("[^0-9]", "");
        String nb = b.replaceAll("[^0-9]", "");
        int len = Math.min(na.length(), nb.length());
        int taille = Math.min(len, 8);
        if (taille == 0) return na.equals(nb);
        return na.substring(na.length() - taille)
                 .equals(nb.substring(nb.length() - taille));
    }
}
