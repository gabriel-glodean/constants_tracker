package org.glodean.opstool;
import org.glodean.opstool.command.ClearCommand;
import org.glodean.opstool.command.ClearDemoCommand;
import org.glodean.opstool.command.Command;
import org.glodean.opstool.command.SeedAuthUserCommand;
import org.glodean.opstool.command.SeedCommand;
import org.glodean.opstool.util.Log;
/** Entry point for the ops-tool native binary. */
public class Main {
    private static final Log log = Log.getLogger(Main.class);
    public static void main(String[] args) {
        Config config;
        try {
            config = Config.fromEnv();
        } catch (IllegalArgumentException e) {
            System.err.println("[ops-tool] Configuration error: " + e.getMessage());
            System.exit(1);
            return;
        }
        log.info("Running command: {}", config.cmd());
        Command command =
                switch (config.cmd()) {
                    case "seed" -> new SeedCommand(config);
                    case "clear" -> new ClearCommand(config);
                    case "clear-demo" -> new ClearDemoCommand(config);
                    case "seed-auth-user" -> new SeedAuthUserCommand(config);
                    default ->
                            throw new IllegalStateException(
                                    "Unknown command: " + config.cmd());
                };
        try {
            command.execute();
            log.info("Command '{}' completed successfully.", config.cmd());
        } catch (Exception e) {
            log.error("Command '{}' failed: {}", config.cmd(), e.getMessage(), e);
            System.exit(1);
        }
    }
}