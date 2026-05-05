package org.glodean.opstool.command;

import java.io.File;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import org.glodean.opstool.Config;
import org.glodean.opstool.client.AppHttpClient;
import org.glodean.opstool.util.Log;

/** Uploads demo JARs (v1 + v2) and calls sync/finalize for each version. */
public class SeedCommand implements Command {

    private static final Log log = Log.getLogger(SeedCommand.class);

    private final Config cfg;

    public SeedCommand(Config cfg) {
        this.cfg = cfg;
    }

    @Override
    public void execute() throws Exception {
        var client = new AppHttpClient(cfg.appUrl());

        if (cfg.authEnabled()) {
            client.login(cfg.demoUsername(), cfg.demoPassword());
        }

        log.info("=== Seeding v1 ===");
        uploadJarDir(client, cfg.jarDirV1());
        client.syncAndFinalize(cfg.project(), 1);

        log.info("=== Seeding v2 ===");
        uploadJarDir(client, cfg.jarDirV2());
        client.syncAndFinalize(cfg.project(), 2);

        log.info("=== Seed complete ===");
    }

    private void uploadJarDir(AppHttpClient client, String dir) throws Exception {
        File[] jars = new File(dir).listFiles(f -> f.getName().endsWith(".jar"));
        if (jars == null || jars.length == 0) {
            log.warn("No JARs found in {}", dir);
            return;
        }
        Arrays.sort(jars, Comparator.comparing(File::getName));
        for (File jar : jars) {
            log.info("Uploading: {}", jar.getName());
            client.uploadJar(cfg.project(), jar.getName(), Files.readAllBytes(jar.toPath()));
        }
    }
}
