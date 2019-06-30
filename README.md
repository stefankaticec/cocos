# COCOS Command and Control Service

This will be a secure command and control service implementation. It creates a star topology with a 
hub in the middle, and clients and servers connecting to it.

A server connects to the hub and gets to know all clients that are connected to the
hub as "client proxies". The server can send commands to a specific client using such a proxy. 
Servers can act as one entity by being in a cluster. Clients usually address their requests to
a cluster, and the hub server will pick one of the servers for that cluster to forward the
request to. When a client receives a command from some server it will however always respond
directly back to the server that sent the command (through the hub).

A hub client only knows of one or more hubs, and sends commands to some server 
through the hub. For this to work the client must know the identification of the
server to reach.

This is a work in progress and not ready.
