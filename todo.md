# TODO

## HIGH PRIORITY
- [ ] Implementing `scalability` features for the server to handle a larger number of clients and messages. This involves vertical scaling (optimizing performance on a single server) and horizontal scaling (distributing the load across multiple servers).
horizontal scaling : know in the client how many servers there are =n , then do %n to know wich server to contact

## MEDIUM PRIORITY

- [ ] Make sure active chats are polled at constant rate and sends are instantly.
- [ ] return false whenever there is a collision (server side)

## LOW PRIORITY
- [ ] Adding `recoverability` to the client-side: recovery mechanisms to handle corrupted messages or unexpected shutdowns.
- [ ] Implementing a `denial-of-service` (DoS) protection mechanism to prevent abuse of the bulletin board service.
