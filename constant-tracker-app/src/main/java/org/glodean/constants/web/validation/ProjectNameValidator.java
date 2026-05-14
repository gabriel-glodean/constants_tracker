package org.glodean.constants.web.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;


class ProjectNameValidator implements ConstraintValidator<ValidProjectName, String> {

    private static final String ALLOWED_EXTRA = ".-_";

    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null || value.isEmpty()) return true; // null/empty = all-projects (optional param)
        return value.chars().allMatch(c ->
            Character.isLetterOrDigit(c) || ALLOWED_EXTRA.indexOf(c) >= 0
        );
    }
}
