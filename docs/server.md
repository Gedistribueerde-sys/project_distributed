# Server Module

This module contains the server-side code.

## `Server.java`

This file contains the main class for the server. It creates an instance of the `BulletinBoardImpl` and registers it with the RMI registry.

- **`main(String[] args)`**: The entry point of the server application.

## `BulletinBoardImpl.java`

This file is the implementation of the `BulletinBoard` interface. It uses a list of concurrent hash maps to store the bulletin board entries.

- **`add(long idx, byte[] value, String tag)`**: Adds a message to the bulletin board at the given index. The index is computed using a modulo operation on the board size.
- **`get(long idx, String preimage)`**: Retrieves a message from the bulletin board. It computes the tag from the preimage and then looks for the message in the corresponding cell. If found, the message is removed from the board.
- **`computeIndex(long idx)`**: Computes the index in the board for a given `idx`.
