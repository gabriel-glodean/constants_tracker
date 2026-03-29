import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

public class UserController implements HttpHandler {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        userService.handle(exchange);
    }
}

