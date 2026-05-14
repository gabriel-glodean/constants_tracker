package org.glodean.constants.web.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProjectNameValidatorTest {

    private final ProjectNameValidator validator = new ProjectNameValidator();

    @ParameterizedTest
    @ValueSource(strings = {
        "demo",
        "my-app",
        "spring-boot",
        "jdk",
        "demo-crud-server",
        "demo-crud-server-v2",
        "com.example",
        "org.glodean.constants",
        "app_v2",
        "a",
        "A1.b-c_d"
    })
    void validProjectNames(String name) {
        assertThat(validator.isValid(name, null)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "my app",
        "my+app",
        "hello:world",
        "foo*bar",
        "foo?bar",
        "foo[bar]",
        "foo\"bar",
        "foo!bar",
        "foo/bar",
        "foo;bar",
        "foo&bar",
        "foo|bar",
        "foo~bar",
        "foo^bar",
        "foo{bar}",
        "foo(bar)",
        "foo\\bar"
    })
    void invalidProjectNames(String name) {
        assertThat(validator.isValid(name, null)).isFalse();
    }

    @org.junit.jupiter.api.Test
    void nullIsValid() {
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @org.junit.jupiter.api.Test
    void emptyIsValid() {
        // empty = omitted project = search all projects
        assertThat(validator.isValid("", null)).isTrue();
    }
}
