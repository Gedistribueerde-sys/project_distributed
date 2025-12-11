# Client Module

This module contains the client-side code for the application.

## `Client.java`

The main entry point for the client application. It initializes the `ChatCore` and launches the JavaFX `GUI`.

## `ChatState.java`

Represents the state of a single chat with another user. It holds the sending and receiving keys, indexes, and tags, as well as the message history.

## `DatabaseManager.java`

Manages the local SQLite database for each user. The entire database file is encrypted using a key derived from the user's password, which is stored in a Java Keystore.

- **`upsertChatState(...)`**: Saves or updates a chat state in the database.
- **`getChatState(...)`**: Retrieves a chat state from the database.
- **`loadAllChatStates()`**: Loads all chat states for the current user.
- **`addMessage(...)`**: Adds a message to the database.
- **`loadMessages(...)`**: Loads all messages for a specific chat.
- **`markMessageAsSent(...)`**: Marks a message as sent to the server.
- **`getPendingOutboxMessages()`**: Retrieves messages that have not yet been sent to the server.
- **`markMessageAsSentAndUpdateState(...)`**: Transactionally marks a message as sent and updates the chat state.
- **`addReceivedMessageAndUpdateState(...)`**: Transactionally adds a received message and updates the chat state.
- **`saveUserUuid(...)`**: Saves the user's UUID to the database.
- **`getUserUuid()`**: Retrieves the user's UUID from the database.
- **`renameChat(...)`**: Renames a chat session.
- **`saveProposedSendValues(...)`**: Saves the proposed next state values for a sent message.
- **`getUnconfirmedMessages()`**: Retrieves all unconfirmed received messages.
- **`deletePendingConfirmation(...)`**: Deletes a pending confirmation entry after the message has been confirmed.

### Database Schema
The client's database (`user_<username>.db`) contains the following tables:
- `user_settings`: Stores user-specific settings, like the user's UUID.
- `chat_sessions`: Stores the state of each chat session, including keys, indexes, and tags.
- `messages`: Stores all messages, both sent and received. It also includes columns to support the two-phase send protocol (`proposed_next_idx`, `proposed_next_tag`, `proposed_next_key`).
- `pending_confirmations`: Stores information about received messages that have been processed by the client but not yet confirmed with the server. This ensures that the client can recover from a crash and confirm the messages later.


## `InAndOutBox.java`

A multi-threaded runnable class that processes the outbox and inbox. It is responsible for all communication with the server.

- **Multi-threaded Processing**: It uses separate threads for sending messages, fetching messages for the active chat (low latency), and fetching messages for background chats. This ensures that the UI remains responsive and that messages are sent and received efficiently.
- **Two-Phase Send**: To ensure idempotent retries, sending a message is a two-phase process. First, the proposed next state (next index, tag, and key) is persisted to the database. Then, the message is sent to the server. If the send fails, it can be retried later using the same persisted state.
- **Proof-of-Work**: Before sending a message, it computes a proof of work using `ProofOfWork.computeProof`. This is required by the server to prevent abuse.
- **Two-Phase Receive**: Receiving a message is also a two-phase process. First, the message is fetched from the server using `get`. After the client has processed the message, it is stored in a `pending_confirmations` table. A separate process then confirms the message with the server using `confirm`.
- **Error Handling**: It implements exponential backoff for retries when the server is unavailable. It also has a "poison pill" mechanism to handle messages that cannot be decrypted, preventing a chat from getting stuck.
- **`run()`**: The main loop of the processor thread.
- **`ensureConnected()`**: Ensures a connection to the RMI bulletin board is established.
- **`processOneOutboxMessageSafely()`**: Processes one message from the outbox.
- **`processActiveChatMessage()`**: Processes messages for the currently active chat.
- **`processBackgroundInboxMessages()`**: Processes inbox messages for all non-active chats.

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

This package contains all the JavaFX GUI components.

### `GUI.java`

The main JavaFX application class. It manages the different scenes (startup, login, register, chat) and the theme.

### `Message.java`

A record that represents a chat message. It contains the sender, the text, whether it was sent by the user, the status of the message, and a timestamp.

- **`MessageStatus`**: An enum representing the status of a sent message:
    - `PENDING`: The message is waiting to be sent to the server.
    - `SENT`: The message has been successfully sent to the server.
    - `DELIVERED`: The message has been received by the recipient.

### `MessageCell.java`

A custom `ListCell` for displaying chat messages in a bubble format. It handles the layout of incoming and outgoing messages, including the message text, timestamp, and status indicator. It also displays a colored avatar with the first letter of the sender's name for incoming messages.

### `ChatState.java`

This class holds the state of a chat with a specific recipient. It contains:
- The recipient's name and UUID.
- The sending and receiving keys, indexes, and tags.
- A list of messages in the chat.
- A `poisonedBackoffUntil` timestamp. If a message from a chat cannot be decrypted, this timestamp is set to a future time to prevent the client from repeatedly trying to process a "poisoned" message.
