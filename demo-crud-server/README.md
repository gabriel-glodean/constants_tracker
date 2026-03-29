# Demo CRUD Server (Pure Java)

This is a fully functional, framework-free Java application that starts an HTTP server and connects to a database via JDBC. It is designed to exercise as many CoreSemanticType values as possible for testing the constant-extractor-lib.

## Features
- Pure Java (no frameworks)
- Starts an HTTP server (com.sun.net.httpserver.HttpServer)
- Exposes CRUD endpoints for `/users`
- Connects to an embedded SQLite database via JDBC
- Uses constants for SQL, URLs, property keys, etc.

## How to Build & Run

### 1. Build the JAR

From the project root, run:

```powershell
./gradlew :demo-crud-server:build
```

The JAR will be created at `demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar`.

### 2. Run the JAR

You must include the sqlite-jdbc dependency on the classpath. From the project root:

```powershell
java -cp "demo-crud-server/build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar;demo-crud-server/build/libs/sqlite-jdbc-3.45.1.0.jar" Main
```

Or, if running from the demo-crud-server directory:

```powershell
java -cp "build/libs/demo-crud-server-0.1.0-SNAPSHOT.jar;build/libs/sqlite-jdbc-3.45.1.0.jar" Main
```

> Note: If the sqlite-jdbc JAR is not present in `build/libs`, you can copy it from your local Maven repository or download it from [here](https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.1.0/sqlite-jdbc-3.45.1.0.jar).

### Test endpoints (e.g. with curl or Postman):
- `GET  http://localhost:8080/users`
- `POST http://localhost:8080/users` (with JSON body)
- `GET  http://localhost:8080/users/{id}`
- `PUT  http://localhost:8080/users/{id}`
- `DELETE http://localhost:8080/users/{id}`

## Dependencies
- JDK 17+
- [sqlite-jdbc-3.45.1.0.jar](https://github.com/xerial/sqlite-jdbc/releases)

## Note
- This is for demonstration and static analysis only. Not for production use.
