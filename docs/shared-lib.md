# Shared-lib Module

This module contains the shared code between the client and the server.

## `BulletinBoard.java`

This file defines the remote interface for a bulletin board. This interface is used by both the client and the server. It extends `java.rmi.Remote`.

-   **`add(long idx, byte[] value, String tag, byte[] proof)`**: Adds a new entry to the bulletin board. It requires a `proof` of work to be submitted.
-   **`get(long idx, String preimage)`**: Retrieves an entry from the bulletin board. This is the first step of the two-phase commit protocol.
-   **`confirm(long idx, String preimage)`**: Confirms the retrieval of an entry. This is the second step of the two-phase commit protocol.

## `Encryption.java`

This file provides encryption utilities.

-   **`preimageToTag(String preimage)`**: A hash function that converts a string preimage to a base64 encoded SHA-256 hash.

## `Pair.java`

This file defines a simple record to hold a pair of values, a byte array and a string. It is used to return a value and a tag from the `get` method of the `BulletinBoard`.

-   **`Pair(byte[] value, String tag)`**

## `ProofOfWork.java`

This utility class provides the methods to compute and verify the proof of work required by the server's `add` method.

-   **`computeProof(long idx, byte[] value, String tag)`**: Computes a proof of work by finding a nonce such that the SHA-256 hash of `(idx, value, tag, nonce)` has a certain number of leading zeros.
-   **`verifyProof(long idx, byte[] value, String tag, byte[] proof)`**: Verifies a proof of work submitted by a client.

## `chat.proto`

This file defines the protobuf messages used for serialization.

### `ChatPayload`

This message is the encrypted payload of a chat message.

-   `string message`: The encrypted chat message.
-   `int64 next_idx`: The index for the *next* message in the hash chain.
-   `bytes next_tag`: The tag for the *next* message in the hash chain.

### `KeyInfo`

This message is used to exchange the initial information to set up a chat.

-   `bytes key`: The initial symmetric key for the chat.
-   `int64 idx`: The starting index for the chat.
-   `bytes tag`: The initial tag for the chat.
