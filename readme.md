# Privately and Unlinkably Exchanging Messages Using a Public Bulletin Board

## Overview

This protocol presents a secure, privacy-focused method for asynchronous, unidirectional message transmission using a public bulletin board. The design hides both message content and communication metadata, including the social graph, from adversaries and service providers. It enables users to send messages even when recipients are offline, prevents linking send/receive events, and provides strong privacy guarantees.

***

## Protocol Introduction

- **Messaging Scenario:** Designed for cases where parties (Alice and Bob) want to communicate securely and anonymously, such as journalists with informants in hostile environments.
- **Privacy Goal:** All metadata (who is communicating when) and content remain confidential, even from the host server, which is assumed honest-but-curious.
- **Centralized Approach:** While peer-to-peer systems struggle with device availability, the protocol uses a central bulletin board accessed via an anonymizing network.

***

## Requirements

The protocol satisfies:

- **Correctness:** Messages are delivered unaltered and in order.
- **Confidentiality:** Only sender and receiver access the content.
- **Integrity & Authenticity:** Only valid senders can send, only the intended recipient can receive.
- **Unlinkability:** No two send/receive events (besides exact message pairs) or social ties can be linked.
- **Forward Security:** Past contacts and traffic remain untraceable upon device compromise.
- **Availability:** Senders can always transmit; messages are eventually delivered.[1]

***

## System Model

- **Server:** Hosts a public bulletin board (array of n cells).
- **Users:** Connect via personal, trusted devices over a mixing network (e.g., Mixminion, Sphinx).
- **Mixing Network:** Obfuscates sender and receiver identities, blending protocol traffic with other applications.
- **API:** Server exposes simple `add` and `get` cell functions to store and retrieve messages.[1]

***

## Threat Model

- **Honest-but-curious Server:** Follows protocol but tries to infer links.
- **Users:** Non-malicious, but may lose device security.
- **Mixing Network:** Assumed to provide probabilistic unlinkability between accesses.

***

## Cryptographic Primitives

- **Authenticated Encryption:** Symmetric key $$K_{AB}$$, unique per sender-receiver pair, with one-time keys for each message.
- **Key Derivation Function (KDF):** Ensures forward security by updating keys per message.
- **Tagging:** A tag (cryptographic hash) for each message identifies recipients privately.

***

## Protocol Functions

Three main functions define the protocol:

| Function        | Purpose                                     |
|-----------------|---------------------------------------------|
| `setup_AB`      | Initialize keys, state, indices, tags.      |
| `send_AB(m)`    | Sender (Alice) sends message $$m$$ to Bob.  |
| `receive_AB`    | Receiver (Bob) retrieves new messages.      |[1]

***

## Implementation Steps

### 1. Setup and Key Exchange

- Alice and Bob securely establish a shared symmetric key ($$K_{AB}$$), initial cell index, and tag.
- Exchange can be done via proximity (e.g., bump phones, scan QR codes), ensuring no linkable IDs are involved.[1]

### 2. Sending Messages

#### Sender Protocol (`send_AB(m)`):

1. Generate random new cell index and tag for next message.
2. Encrypt the message using authenticated encryption: $$u = \text{Enc}_{K_{AB}}(m, \text{next\_idx}, \text{next\_tag})$$.
3. Add encrypted value with current tag to selected cell: `add(current_idx, u, current_tag)`.
4. Update sender's state (key via KDF, next index, next tag).

#### Pseudocode

```text
function send_AB(m):
    idx ← random(0, n-1)
    tag ← random(TagSpace)
    u ← Enc_KAB(m, idx, tag)
    add(current_idx, u, current_tag)
    current_idx, current_tag ← idx, tag
    KAB ← KDF(KAB)
```


### 3. Receiving Messages

#### Receiver Protocol (`receive_AB`):

1. Retrieve value from the designated cell: `get(current_idx, current_tag)`.
2. If returned value is valid, decrypt: `open_KAB(u)`.
3. Update receiver’s state (key via KDF, next idx, next tag).
4. Delete message from cell to prevent replay.

#### Pseudocode

```text
function receive_AB():
    u ← get(current_idx, current_tag)
    if u is valid:
        m, idx, tag ← Dec_KAB(u)
        current_idx, current_tag ← idx, tag
        KAB ← KDF(KAB)
        return m
    else:
        return None
```


### 4. Bulletin Board API

- `add(i, v, t)`: Adds value-tag pair to cell $$i$$.
- `get(i, b)`: If $$(v, t)$$ for preimage $$b$$ found in cell $$i$$, returns $$v$$ and deletes it, else returns empty.

***

## Implementation Notes

- **State Synchronization:** Keys, tags, and indices must be kept in sync between sender and receiver. State corruption detection can be enhanced using hashing the local state.
- **Forward Security:** Keys evolve per message, reducing risk after compromise.
- **Unlinkability:** Random cell access, combined with mix network, prevents linking communications or user relationships.
- **Scalability:** Multiple servers can host partitions of the board; cell load can be balanced as user numbers grow.
- **Denial-of-Service Mitigation:** Techniques like payment, puzzles, send-coins, and registration limit spam and board flooding.
- **Bidirectional Messaging:** Run two instances of the protocol with separate keys, indices, and tags for each direction.
- **Presence Updates:** Can be adapted for presence protocols by broadcasting status changes using the same mechanism.[1]

***

## Example Applications

- **Anonymous Messaging Apps:** WhatsApp/Signal-like apps with metadata protection.
- **Private Presence Service:** Users privately update contacts about online status.
- **Social Graph Protection:** Even hosting on public cloud, relational data remains unlinkable.

***

## Security Analysis

The protocol achieves:

- Message confidentiality and authenticity.
- Metadata privacy—hiding who communicates, when, and how often.
- Forward secrecy.
- Denial-of-service resilience for honest users.
- Scalability and cloud hosting without privacy compromise.[1]

***

## References

Protocol based on the details in “Privately and Unlinkably Exchanging Messages Using a Public Bulletin Board” by Jaap-Henk Hoepman, Radboud University, 2015.[1]

