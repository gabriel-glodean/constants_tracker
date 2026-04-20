package org.glodean.constants.extractor.configfile;

import org.glodean.constants.model.SourceKind;

/** Source kinds for configuration file extraction. */
public enum ConfigFileSourceKind implements SourceKind {
    YAML,
    PROPERTIES,
    TOML;
}

