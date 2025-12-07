# Secure Messaging Application

This is a secure messaging application built with Java. It features a client-server architecture where the client holds all the logic and the server acts as a simple bulletin board. The communication between the client and the server is done using Java RMI.

## Project Structure

The project is divided into three Maven modules:

-   `MessageApp/server`: The server application that runs the bulletin board.
-   `MessageApp/client`: The client application, a JavaFX GUI that allows users to send and receive secure messages.
-   `MessageApp/shared-lib`: A shared library containing common code used by both the client and the server, including the RMI interface and Protobuf message definitions.

## How to Build and Run

### Prerequisites

-   Java (JDK 16 or higher)
-   Apache Maven

### Build

To build the project, run the following command from the `MessageApp` directory:

```bash
mvn clean install
```

This will build all three modules and create the necessary JAR files.

### Run the Servers

To run the servers, execute the following command from the `MessageApp` directory:

**Server 1:**
```bash
mvn exec:java -pl server -Dexec.args="1100"
```

**Server 2:**
```bash
mvn exec:java -pl server -Dexec.args="1099"
```

The servers will start and listen for RMI connections.

NOTE: If you want more servers, adjust the client code to add the ports of the servers to the list.

### Run the Client

To run the client, execute the following command from the `MessageApp` directory:

```bash
mvn exec:java -pl client
```

The client GUI will start. You can then register a new user or log in with an existing user.

## Documentation

For more detailed information about the project's architecture and modules, please refer to the documents in the [`docs`](./docs) folder.
