package org.glodean.constants.dto;

import java.io.Serializable;

public record GetClassConstantsRequest(String clazz, String project, int version) implements Serializable {
}
