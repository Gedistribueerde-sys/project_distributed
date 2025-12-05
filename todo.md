# TODO

## HIGH PRIORITY
- [ ] Implementing `scalability` features for the server to handle a larger number of clients and messages. This involves vertical scaling (optimizing performance on a single server) and horizontal scaling (distributing the load across multiple servers).

## MEDIUM PRIORITY
- [ ] Fix that send and received messages don't depend on the name of the sender, now if we have same name as sender, message get displayed as we sent it.
- [ ] Giving chats and ID for when 2 persons have the same name (another way to distinct using circles with color?)
- [ ] Make sure active chats are polled at constant rate and sends are instantly.
- 
## LOW PRIORITY
- [ ] Adding `recoverability` to the client-side: recovery mechanisms to handle corrupted messages or unexpected shutdowns.
- [ ] Implementing a `denial-of-service` (DoS) protection mechanism to prevent abuse of the bulletin board service.
