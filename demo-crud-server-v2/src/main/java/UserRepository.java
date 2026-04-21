import java.sql.*;
public class UserRepository {
    private static final String SQL_CREATE_TABLE = "CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT, email TEXT NOT NULL)";
    private static final String SQL_INSERT_USER = "INSERT INTO users (email) VALUES (?)";
    private static final String SQL_SELECT_ALL = "SELECT * FROM users";
    private static final String SQL_SELECT_ONE = "SELECT * FROM users WHERE id = ?";
    private static final String SQL_UPDATE_USER = "UPDATE users SET email = ? WHERE id = ?";
    private static final String SQL_DELETE_USER = "DELETE FROM users WHERE id = ?";
    private static final String DB_URL_PREFIX = "jdbc:sqlite:";
    private static final String JSON_KEY_ID = "id";
    private final String dbUrl;
    public UserRepository(String dbFile) throws Exception {
        this.dbUrl = DB_URL_PREFIX + dbFile;
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(SQL_CREATE_TABLE);
            }
        }
    }
    public String listUsers() throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery(SQL_SELECT_ALL);
                StringBuilder sb = new StringBuilder();
                sb.append("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    sb.append("{\"").append(JSON_KEY_ID).append("\":").append(rs.getInt("id"))
                      .append(",\"email\":\"").append(rs.getString("email")).append("\"}");
                    first = false;
                }
                sb.append("]");
                return sb.toString();
            }
        }
    }
    public String getUser(int id) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ONE)) {
                ps.setInt(1, id);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return "{\"" + JSON_KEY_ID + "\":" + rs.getInt("id") + ",\"email\":\"" + rs.getString("email") + "\"}";
                } else {
                    return null;
                }
            }
        }
    }
    public String createUser(String email) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT_USER, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, email);
                ps.executeUpdate();
                ResultSet rs = ps.getGeneratedKeys();
                int id = rs.next() ? rs.getInt(1) : -1;
                return "{\"" + JSON_KEY_ID + "\":" + id + ",\"email\":\"" + email + "\"}";
            }
        }
    }
    public String updateUser(int id, String email) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_USER)) {
                ps.setString(1, email);
                ps.setInt(2, id);
                int updated = ps.executeUpdate();
                if (updated > 0) {
                    return "{\"" + JSON_KEY_ID + "\":" + id + ",\"email\":\"" + email + "\"}";
                } else {
                    return null;
                }
            }
        }
    }
    public boolean deleteUser(int id) throws Exception {
        try (Connection conn = DriverManager.getConnection(dbUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(SQL_DELETE_USER)) {
                ps.setInt(1, id);
                int deleted = ps.executeUpdate();
                return deleted > 0;
            }
        }
    }
}
