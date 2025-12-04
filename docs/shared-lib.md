# Shared-lib Module

This module contains the shared code between the client and the server.

## `BulletinBoard.java`

This file defines the remote interface for a bulletin board. This interface is used by both the client and the server.

- **`add(long idx, byte[] value, String tag)`**: Adds a new entry to the bulletin board.
- **`get(long idx, String preimage)`**: Retrieves an entry from the bulletin board.

## `Encryption.java`

This file provides encryption utilities.

- **`preimageToTag(String preimage)`**: A hash function that converts a string preimage to a base64 encoded SHA-256 hash.

## `Pair.java`

This file defines a simple record to hold a pair of values, a byte array and a string. It is used to return a value and a tag from the `get` method of the `BulletinBoard`.

- **`Pair(byte[] value, String tag)`**

## `chat.proto`

This file defines the protobuf messages used for communication.

- **`ChatPayload`**: A message containing a chat message, the next index, and the next tag.
  - `string message`
  - `int64 next_idx`
  - `bytes next_tag`
- **`KeyInfo`**: A message containing a key, a starting index, and a tag.
  - `bytes key`
  - `int64 idx`
  - `bytes tag`
