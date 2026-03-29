import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class Main {
    private static final String BASE_URL = "http://localhost:8080/users";

    public static void main(String[] args) throws Exception {
        UserRepository userRepository = new UserRepository();
        UserService userService = new UserService(userRepository);
        UserController userController = new UserController(userService);
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/users", userController);
        server.setExecutor(null);
        System.out.println("Server started at " + BASE_URL);
        server.start();
    }
}
