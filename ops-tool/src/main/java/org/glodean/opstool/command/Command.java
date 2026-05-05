package org.glodean.opstool.command;

@FunctionalInterface
public interface Command {
    void execute() throws Exception;
}

