# Architecture Decisions

This document outlines the key architectural decisions made in the development of this secure messaging application.

## Overview

The application follows a **client-server architecture** with a strong emphasis on client-side intelligence.

-   **Server**: A minimalist, stateless bulletin board implemented using Java RMI. Its sole responsibility is to accept encrypted messages and deliver them to the intended recipient upon request. It does not persist messages after they are retrieved.
-   **Client**: A JavaFX desktop application that contains all the core logic. It manages user accounts, cryptographic keys, secure chat sessions, and local data persistence.
-   **Shared Library**: A common module containing the RMI interface, data transfer objects (DTOs), and protocol definitions (Protobuf) shared between the client and server.

## Key Architectural Decisions

### 1. Dumb Server, Smart Client

The design intentionally keeps the server as simple as possible.

-   **Rationale**:
    -   **Security**: The server stores no sensitive user data, minimizing the impact of a server-side breach. All encryption and key management happens on the client.
    -   **Scalability & Simplicity**: A stateless server is easier to scale and maintain. The complexity is managed on the client side, isolating user data to their own devices.

### 2. Java RMI for Client-Server Communication

Java Remote Method Invocation (RMI) was chosen for communication between the client and the server.

-   **Rationale**:
    -   **Simplicity**: RMI is a natural choice for Java-to-Java remote procedure calls, simplifying the network communication layer.
    -   **Focus**: It allows the focus to remain on the application's core security features rather than the intricacies of network protocols.

### 3. Local-First, Encrypted Persistence

All user data, including chat history and cryptographic keys for each chat, is stored locally on the client's machine in an encrypted SQLite database.

-   **Rationale**:
    -   **Confidentiality**: User data is never exposed in plaintext on the server or in transit. It remains encrypted at rest on the user's device.
    -   **Control**: Users have full control over their data.

### 4. Secure Key Management with a Java Keystore

Each user has a password-protected PKCS#12 keystore. This keystore holds a master key which is used to encrypt the local SQLite database.

-   **Rationale**:
    -   **Strong Security**: The keystore provides a robust, standard-based mechanism for protecting the user's most critical key. Access to any user data requires the keystore password.
    -   **Layered Encryption**: This creates a two-layer encryption scheme: the chat keys are encrypted in the database, and the entire database is encrypted by a key from the keystore.

### 5. Protocol Buffers (Protobuf) for Serialization

The payload of each chat message (the actual message content and the next-step information for the hash chain) is serialized using Google's Protocol Buffers.

-   **Rationale**:
    -   **Efficiency**: Protobuf is a compact and efficient binary format, reducing the size of the data transmitted to the server.
    -   **Clarity & Evolution**: The message structure is clearly defined in a `.proto` file, which serves as self-documenting and allows for easier evolution of the protocol over time.

### 6. Asynchronous Messaging with a Bulletin Board Model

Clients do not communicate directly. Instead, they post encrypted messages to a public bulletin board (the server) and retrieve them from it. A background thread on the client (`InAndOutBox`) handles the sending and receiving of messages.

-   **Rationale**:
    -   **Decoupling**: Senders and receivers do not need to be online at the same time.
    -   **Resilience**: The background thread can retry sending or receiving messages if the server is temporarily unavailable, improving the reliability of message delivery.

### 7. Hash-Chain Protocol for Forward Secrecy

The core of the secure messaging protocol is a hash-chain mechanism. For each message sent, the symmetric encryption key is updated by hashing it.

-   **Rationale**:
    -   **Forward Secrecy**: If an attacker compromises a user's current key, they cannot decrypt past messages, as the previous keys in the chain are not derivable from the current one.
    -   **Self-Healing**: While not providing full self-healing (post-compromise security), it contains the damage to future messages only.
