# Server Module

This module contains the server-side code for the application. It implements a reliable, persistent, and scalable bulletin board for the messaging system.

## `Server.java`

This is the main entry point for the server application. It is responsible for:
- Initializing the `ServerDatabaseManager`.
- Instantiating the `BulletinBoardImpl` with the saved state from the database.
- Binding the `BulletinBoardImpl` instance to the Java RMI registry so that clients can connect to it.

## `BulletinBoardImpl.java`

This is the core of the server's logic, implementing the `BulletinBoard` RMI interface. It provides a scalable and reliable message board with a two-phase commit protocol for message retrieval.

### Key Features:

-   **Board Generations**: The bulletin board is implemented using a series of "generations". Each generation has a larger capacity than the previous one. When the current generation becomes too full, a new, larger generation is created and becomes the active one. This allows the board to scale dynamically without blocking.
-   **Two-Phase Commit**: Message retrieval is a two-step process to ensure reliability:
    1.  **`get(long idx, String preimage)`**: A client calls this method to check out a message. The message is not immediately deleted but is moved to a temporary "checked-out" collection and marked with a timestamp.
    2.  **`confirm(long idx, String preimage)`**: After the client has successfully processed the message, it calls this method to confirm receipt. The server then permanently deletes the message from its persistent storage.
-   **Automated Cleanup**: A background thread runs periodically to clean up "orphaned" messages. If a message has been checked out (`get`) but not confirmed (`confirm`) within a certain time frame (e.g., because the client crashed), the cleanup task returns the message to the main board so it can be retrieved again.

## `ServerDatabaseManager.java`

This class handles all persistence for the server using a local SQLite database. It ensures that the server can be restarted without losing messages.

-   **`initializeDatabase()`**: Creates the necessary database tables if they don't exist.
-   **`saveMessage(...)`**: Saves a message to the database. This is called when a client successfully `add`s a message.
-   **`deleteMessage(...)`**: Deletes a message from the database. This is called when a client `confirm`s a message.
-   **`loadAllMessagesWithCapacity()`**: Loads all persisted messages and the board capacity from the database when the server starts. This is crucial for restoring the server's state.

### Database Schema

The server's database (`identifier.sqlite`) contains two tables:

1.  `board`:
    -   `message_id` (INTEGER PRIMARY KEY): A unique ID for each message.
    -   `idx` (BIGINT): The board index for the message.
    -   `tag` (TEXT): The message's tag.
    -   `value` (BLOB): The encrypted message content.
2.  `board_capacity`:
    -   `capacity` (BIGINT): The last known capacity of the board. This is used to initialize the `BulletinBoardImpl` with the correct size on startup.
