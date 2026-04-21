import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Pattern;

public class UserService {
    private static final String EMAIL_REGEX = "^[A-Za-z0-9+_.-]+@(.+)$";
    private static final String LOG_MSG_CREATE = "[INFO] Creating user: {}";
    private static final String ERR_USER_NOT_FOUND = "User not found";
    private static final String ENCODING = "UTF-8";
    private static final String MIME_JSON = "application/json";
    private static final String JSON_KEY_ID = "id";

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");
        int id = -1;
        if (parts.length == 3) {
            try { id = Integer.parseInt(parts[2]); } catch (Exception ignored) {}
        }
        try {
            if ("GET".equals(method) && id == -1) {
                handleList(exchange);
            } else if ("GET".equals(method)) {
                handleGet(exchange, id);
            } else if ("POST".equals(method)) {
                handleCreate(exchange);
            } else if ("PUT".equals(method)) {
                handleUpdate(exchange, id);
            } else if ("DELETE".equals(method)) {
                handleDelete(exchange, id);
            } else {
                send(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
        } catch (Exception e) {
            send(exchange, 500, "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private void handleList(HttpExchange exchange) throws Exception {
        send(exchange, 200, userRepository.listUsers());
    }

    private void handleGet(HttpExchange exchange, int id) throws Exception {
        String user = userRepository.getUser(id);
        if (user == null) {
            send(exchange, 404, "{\"error\":\"" + ERR_USER_NOT_FOUND + "\"}");
        } else {
            send(exchange, 200, user);
        }
    }

    private void handleCreate(HttpExchange exchange) throws Exception {
        String body = readBody(exchange.getRequestBody());
        String email = extractEmail(body);
        System.out.println(LOG_MSG_CREATE.replace("{}", email));
        if (!Pattern.matches(EMAIL_REGEX, email)) {
            send(exchange, 400, "{\"error\":\"Invalid email\"}");
            return;
        }
        String user = userRepository.createUser(email);
        send(exchange, 201, user);
    }

    private void handleUpdate(HttpExchange exchange, int id) throws Exception {
        String body = readBody(exchange.getRequestBody());
        String email = extractEmail(body);
        String user = userRepository.updateUser(id, email);
        if (user == null) {
            send(exchange, 404, "{\"error\":\"" + ERR_USER_NOT_FOUND + "\"}");
        } else {
            send(exchange, 200, user);
        }
    }

    private void handleDelete(HttpExchange exchange, int id) throws Exception {
        boolean deleted = userRepository.deleteUser(id);
        if (deleted) {
            send(exchange, 204, "");
        } else {
            send(exchange, 404, "{\"error\":\"" + ERR_USER_NOT_FOUND + "\"}");
        }
    }

    private String readBody(InputStream is) throws IOException {
        Scanner s = new Scanner(is, ENCODING).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private String extractEmail(String body) {
        int idx = body.indexOf("email");
        if (idx == -1) return "";
        int start = body.indexOf(':', idx) + 1;
        int end = body.indexOf('"', start + 1);
        int quote = body.indexOf('"', start);
        if (quote == -1 || end == -1) return "";
        return body.substring(quote + 1, end);
    }

    private void send(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", MIME_JSON);
        exchange.sendResponseHeaders(status, body.getBytes(StandardCharsets.UTF_8).length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
    }
}

