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
            // Both '.' and '/' are valid separators (dots for packages, slashes for paths)
            if (c == '.' || c == '/') {
                int len = i - segmentStart;
                // Reject leading dot, trailing separator, and consecutive separators.
                // Keep allowing a single leading slash (e.g., /BOOT-INF/classes/application.yaml).
                if (len == 0) {
                    if (c == '.' || i > 0) return false;
                    segmentStart = i + 1;
                    continue;
                }
                if (len > 0) { // validate non-empty segments
                    if (!isValidStart(value.charAt(segmentStart))) return false;
                    for (int j = segmentStart + 1; j < i; j++) {
                        if (!isValidPart(value.charAt(j))) return false;
                    }
                }
                segmentStart = i + 1;
            }
        }
        return true;
    }

    // Letter, underscore, or $ — JVM uses $ for nested/anonymous class names
    // Also supports filenames starting with underscore or dollar (e.g. _private.properties, $generated.yml)
    private static boolean isValidStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    // Allow letters, digits, underscores, dollars, and hyphens
    // to support Java class names and filenames like application-prod.properties, _private.yml, $proxy.class
    private static boolean isValidPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '-';
    }
}
