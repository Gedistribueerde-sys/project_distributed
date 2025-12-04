# TODO

- [ ] Adding `recoverability` to the application. This includes for the server to be able to recover its state after a crash or restart. This may involve implementing persistent storage for the bulletin board data. Also adding client-side recovery mechanisms to handle corrupted messages or unexpected shutdowns.
- [ ] Implementing `scalability` features for the server to handle a larger number of clients and messages. This involves vertical scaling (optimizing performance on a single server) and horizontal scaling (distributing the load across multiple servers).
- [ ] Implementing a `denial-of-service` (DoS) protection mechanism to prevent abuse of the bulletin board service.