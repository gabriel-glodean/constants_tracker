package org.glodean.constants.extractor.configfile;

import org.glodean.constants.model.SourceKind;

/** Source kinds for configuration file extraction. */
public enum ConfigFileSourceKind implements SourceKind {
    /** YAML format configuration files. */
    YAML,
    /** Java properties format configuration files. */
    PROPERTIES,
    /** TOML format configuration files. */
    TOML;
}
