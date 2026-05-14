package org.glodean.constants.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

class ClassNameValidator implements ConstraintValidator<ValidClassName, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true; // @NotBlank handles null
        if (value.isEmpty()) return false;

        int segmentStart = 0;
        for (int i = 0; i <= value.length(); i++) {
            char c = (i == value.length()) ? '.' : value.charAt(i);
            if (c == '.') {
                int len = i - segmentStart;
                if (len == 0) return false; // empty segment: leading/trailing/consecutive dot
                if (!isValidStart(value.charAt(segmentStart))) return false;
                for (int j = segmentStart + 1; j < i; j++) {
                    if (!isValidPart(value.charAt(j))) return false;
                }
                segmentStart = i + 1;
            }
        }
        return true;
    }

    // Letter, underscore, or $ — JVM uses $ for nested/anonymous class names
    private static boolean isValidStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isValidPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }
}
