# TODO

## LOW PRIORITY
- [ ] Adding `recoverability` to the client-side: recovery mechanisms to handle corrupted messages.
- [ ] Implementing a `denial-of-service` (DoS) protection mechanism to prevent abuse of the bulletin board service.
- [ ] Application keeps working if 1 of the servers goes down -> implement server redundancy and failover mechanisms.

## NICE TO HAVE
- [ ] Add group chat functionality.
- [ ] Emoji support.
- [ ] File sharing capabilities.

## DDOS
not hard to do, but requires some thought
- so if we just add calculating a hash of the tag + nonce -> problem the attacker can spawn on different indexes:
- solution: require the client to do a proof-of-work for a specific index:
  - client gets a random index from the server
  - client computes a nonce such that hash(tag || index || nonce) has some number of leading zeros
  - client sends (tag, index, nonce, message) to the server
  - server verifies the proof-of-work by checking the hash