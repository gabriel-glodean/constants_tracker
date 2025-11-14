package org.glodean.constants.store;

public interface VersionIncrementer {
  int getNextVersion(String project, String className);
}
