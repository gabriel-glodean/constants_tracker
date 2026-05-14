package org.glodean.constants.web.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target({FIELD, PARAMETER, RECORD_COMPONENT})
@Retention(RUNTIME)
@Constraint(validatedBy = ProjectNameValidator.class)
public @interface ValidProjectName {
    String message() default "must be a valid project name";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
