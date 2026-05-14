package org.glodean.constants.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ClassNameValidatorTest {

    private final ClassNameValidator validator = new ClassNameValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "Foo",
        "com.example.Foo",
        "org.glodean.constants.samples.Greeter",
        "Outer$Inner",
        "com.example.Outer$Inner",
        "_Util",
        "$Generated",
        "com.example.$Generated",
        "com.example.Foo$1",       // anonymous class
        "a",
        "A",
        "com.example.FooBar"
    })
    void validClassNames(String name) {
        assertThat(validator.isValid(name, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        ".Foo",             // leading dot
        "Foo.",             // trailing dot
        "foo..Bar",         // consecutive dots
        "123Bad",           // starts with digit
        "com.123.Foo",      // segment starts with digit
        "com.example.Foo Bar", // space
        "com/example/Foo",  // slash (JVM internal format not accepted)
        "com.example.Foo+",
        "com.example.Foo!"
    })
    void invalidClassNames(String name) {
        assertThat(validator.isValid(name, null)).isFalse();
    }

    @Test
    void nullIsValid() {
        // null deferred to @NotBlank
        assertThat(validator.isValid(null, null)).isTrue();
    }
}
