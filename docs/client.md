# Client Module

This module contains the client-side code for the application.

## `Client.java`

The main entry point for the client application. It initializes the `ChatCore` and launches the JavaFX `GUI`.

## `ChatState.java`

Represents the state of a single chat with another user. It holds the sending and receiving keys, indexes, and tags, as well as the message history.

## `DatabaseManager.java`

Manages the local SQLite database for each user. It stores encrypted chat states and messages.

- **`upsertChatState(...)`**: Saves or updates a chat state in the database.
- **`getChatState(...)`**: Retrieves a chat state from the database.
- **`loadAllChatStates()`**: Loads all chat states for the current user.
- **`addMessage(...)`**: Adds a message to the database.
- **`loadMessages(...)`**: Loads all messages for a specific chat.
- **`markMessageAsSent(...)`**: Marks a message as sent to the server.
- **`getPendingOutboxMessages()`**: Retrieves messages that have not yet been sent to the server.
- **`markMessageAsSentAndUpdateState(...)`**: Transactionally marks a message as sent and updates the chat state.
- **`addReceivedMessageAndUpdateState(...)`**: Transactionally adds a received message and updates the chat state.

## `InAndOutBox.java`

A runnable class that processes the outbox and inbox. It sends pending messages to the bulletin board and retrieves new messages. It also handles retries and connection management.

- **`run()`**: The main loop of the processor thread.
- **`ensureConnected()`**: Ensures a connection to the RMI bulletin board is established.
- **`processOneOutboxMessageSafely()`**: Processes one message from the outbox.
- **`processOneInboxMessageSafely()`**: Processes one message from the inbox.

---

## `controller` package

### `ChatCore.java`

The core logic of the client application. It manages chat states, user authentication, and the interaction between the GUI and the backend.

- **`register(String username, String password)`**: Registers a new user.
- **`login(String username, String password)`**: Logs in a user.
- **`logout()`**: Logs out the current user.
- **`createChatWithKeys(...)`**: Creates a new chat with another user.
- **`sendMessage(...)`**: Sends a message to a chat.

### `ChatController.java`

The controller for the main chat view (`ChatView.fxml`). It handles user interactions like sending messages, selecting chats, and logging out.

### `LoginController.java`

The controller for the login view (`LoginView.fxml`).

### `RegisterController.java`

The controller for the registration view (`RegisterView.fxml`).

### `NewChatController.java`

The controller for the new chat dialog (`NewChatView.fxml`). It allows the user to generate and share keys to start a new chat.

### `StartupController.java`

The controller for the initial startup view (`StartupView.fxml`), which gives the user the option to log in or register.

---

## `crypto` package

### `ChatCrypto.java`

Provides cryptographic functions for the chat protocol, such as generating keys, tags, and indexes, and encrypting/decrypting chat payloads.

- **`generateBumpKeyInfo()`**: Generates a new `KeyInfo` protobuf message.
- **`encryptPayloadBytes(...)`**: Encrypts a chat payload.
- **`decryptPayloadBytes(...)`**: Decrypts a chat payload.
- **`makeNewSecretKey(...)`**: Derives a new secret key from an old one.

### `CryptoUtils.java`

Utility class for encrypting and decrypting data at rest (in the database) using AES-GCM.

- **`encrypt(...)`**: Encrypts data.
- **`decrypt(...)`**: Decrypts data.

### `KeyStoreImpl.java`

Manages the user's keystore, which stores the database encryption key.

- **`makeKeyStore(...)`**: Creates a new keystore for a user.
- **`loadKeyStore(...)`**: Loads an existing keystore.
- **`getDatabaseKey()`**: Retrieves the database encryption key.

---

## `GUI` package

### `GUI.java`

The main JavaFX application class. It manages the different scenes (startup, login, register, chat) and the theme.

### `Message.java`

A simple record representing a chat message.

### `MessageCell.java`

A custom `ListCell` for displaying chat messages in a bubble format.
