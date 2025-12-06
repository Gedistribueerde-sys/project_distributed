# TODO

## HIGH PRIORITY
- [ ] Implementing `scalability` features for the server to handle a larger number of clients and messages. This involves vertical scaling (optimizing performance on a single server)

## MEDIUM PRIORITY
- [ ] Make sure active chats are polled at constant rate and sends are instantly.

## LOW PRIORITY
- [ ] Adding `recoverability` to the client-side: recovery mechanisms to handle corrupted messages or unexpected shutdowns.
- [ ] Implementing a `denial-of-service` (DoS) protection mechanism to prevent abuse of the bulletin board service.
- [ ] Application keeps working if 1 of the servers goes down.