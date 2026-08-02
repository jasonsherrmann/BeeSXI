package com.jaysin.beesxi.util;

public final class Utils {
    private Utils() {
    }

    public static String trim(String value, int maxChars) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.replace('_', ' ').replace('-', ' ');
        StringBuilder formatted = new StringBuilder();
        boolean capitalizeNext = true;

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                if (formatted.length() > 0 && formatted.charAt(formatted.length() - 1) != ' ') {
                    formatted.append(' ');
                }
                capitalizeNext = true;
            } else if (Character.isLetter(c)) {
                if (capitalizeNext) {
                    formatted.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    formatted.append(Character.toLowerCase(c));
                }
            } else {
                formatted.append(c);
                capitalizeNext = false;
            }
        }

        String result = formatted.toString().trim();
        if (result.length() <= maxChars) {
            return result;
        }
        return result.substring(0, maxChars - 3) + "...";
    }
}
