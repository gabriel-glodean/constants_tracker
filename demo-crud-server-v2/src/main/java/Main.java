import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();
        UserRepository userRepository = new UserRepository(config.getDbFile());
        UserService userService = new UserService(userRepository);
        UserController userController = new UserController(userService);
        HttpServer server = HttpServer.create(new InetSocketAddress(config.getPort()), 0);
        server.createContext("/users", userController);
        server.setExecutor(null);
        System.out.println("Server started at " + config.getBaseUrl());
        server.start();
    }
}

