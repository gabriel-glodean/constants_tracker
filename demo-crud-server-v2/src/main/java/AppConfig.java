import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
public class AppConfig {
    private static final String CONFIG_FILE = "app.properties";
    private static final String KEY_PORT = "server.port";
    private static final String KEY_HOST = "server.host";
    private static final String KEY_DB_FILE = "db.file";
    private static final String DEFAULT_PORT = "8080";
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_DB_FILE = "demo-users.db";
    private final Properties props;
    public AppConfig() {
        props = new Properties();
        try (InputStream is = AppConfig.class.getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (is != null) {
                props.load(is);
            } else {
                System.out.println("[WARN] " + CONFIG_FILE + " not found on classpath, using defaults.");
            }
        } catch (IOException e) {
            System.out.println("[WARN] Failed to load " + CONFIG_FILE + ": " + e.getMessage());
        }
    }
    public int getPort() {
        return Integer.parseInt(props.getProperty(KEY_PORT, DEFAULT_PORT));
    }
    public String getHost() {
        return props.getProperty(KEY_HOST, DEFAULT_HOST);
    }
    public String getDbFile() {
        return props.getProperty(KEY_DB_FILE, DEFAULT_DB_FILE);
    }
    public String getBaseUrl() {
        return "http://" + getHost() + ":" + getPort() + "/users";
    }
}
