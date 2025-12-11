# Architecture Decisions

This document outlines the key architectural decisions made in the development of this secure messaging application.

## Overview

The application follows a **client-server architecture** implemented in Java.

-   **Server**: A stateful, persistent, and scalable bulletin board implemented using Java RMI. It is responsible for accepting encrypted messages, requiring proof of work, and delivering them to the intended recipient upon request. It uses a two-phase commit protocol for message retrieval and persists messages in a local SQLite database to ensure reliability and scalability.
-   **Client**: A JavaFX desktop application that contains all the core logic. It manages user accounts, cryptographic keys, secure chat sessions, and local data persistence.
-   **Shared Library**: A common module containing the RMI interface, data transfer objects (DTOs), and protocol definitions (Protobuf) shared between the client and server.

## Key Architectural Decisions

### 1. Stateful Server, Smart Client

The design balances the responsibilities between the client and the server.

-   **Rationale**:
    -   **Security**: While the server is stateful, it only stores encrypted data. All encryption and key management happens on the client, minimizing the impact of a server-side breach.
    -   **Scalability & Reliability**: The server's stateful nature, combined with its "Board Generations" resizing strategy and two-phase commit protocol, allows it to be both scalable and reliable. The complexity is managed on both the client and server side.

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

### 5. Proof-of-Work to Prevent Abuse

The server requires clients to perform a proof-of-work calculation before a message can be added to the bulletin board.

-   **Rationale**:
    -   **DDoS Mitigation**: This significantly raises the cost for an attacker to spam the server or conduct a denial-of-service attack, as each message requires a non-trivial amount of computation.
    -   **Fairness**: It ensures that server resources are allocated to legitimate users.

### 6. Protocol Buffers (Protobuf) for Serialization

The payload of each chat message (the actual message content and the next-step information for the hash chain) is serialized using Google's Protocol Buffers.

-   **Rationale**:
    -   **Efficiency**: Protobuf is a compact and efficient binary format, reducing the size of the data transmitted to the server.
    -   **Clarity & Evolution**: The message structure is clearly defined in a `.proto` file, which serves as self-documenting and allows for easier evolution of the protocol over time.

### 7. Asynchronous Messaging with a Reliable Bulletin Board Model

Clients do not communicate directly. Instead, they post encrypted messages to a public bulletin board (the server) and retrieve them from it. A background thread on the client (`InAndOutBox`) handles the sending and receiving of messages.

-   **Rationale**:
    -   **Decoupling**: Senders and receivers do not need to be online at the same time.
    -   **Reliability**: The server uses a two-phase commit (`get`/`confirm`) protocol for message retrieval. A message is only removed from the board after the client has confirmed its receipt. An automated cleanup task on the server handles orphaned messages that are checked out but never confirmed.
    -   **Resilience**: The background thread on the client can retry sending or receiving messages if the server is temporarily unavailable, improving the reliability of message delivery.

### 8. Hash-Chain Protocol for Forward Secrecy

The core of the secure messaging protocol is a hash-chain mechanism. For each message sent, the symmetric encryption key is updated by hashing it.

-   **Rationale**:
    -   **Forward Secrecy**: If an attacker compromises a user's current key, they cannot decrypt past messages, as the previous keys in the chain are not derivable from the current one.
    -   **Self-Healing**: While not providing full self-healing (post-compromise security), it contains the damage to future messages only.
