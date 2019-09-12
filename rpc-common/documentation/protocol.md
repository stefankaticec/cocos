# Packet protocol

## Basic package format

OLD AND WRONG, keeping for historic purposes.

All packets between hub and client have the same format. The general format is as follows:

|Size|Type|Value|Description|
|------|----|-----|-----------|
|4|int32|0xdaeda105|A fixed value that indicates packet start, to ward against sync faults|
|4|int32|(length h)|The length, in bytes, of the packet header that follows
|h|protobuf envelope|-|A protobuf with a fixed format which is the envelope of the command. This protobuf has a fixed format, but is an union type.
|4|int32|(length p)|The length of the payload: the rest of the data.|
|p|byte[]|user defined|The payload of the packet. The format of the payload is determined by information in the header. The payload is only decoded in the endpoints; the hub just streams it to whatever destination as a byte stream|

The packet envelope is a Google Protobuf message, with embedded subtypes representing different states. The 
envelope is the only part that is also decoded by the hub; the payload is not decoded by the hub but just 
copied to whatever destination was identified by the envelope.

The envelope contains the following fixed header fields:

     int32 version = 1;
     string sourceId = 2;
     string targetId = 3;
     string command = 4;
     string commandId = 5;
     string dataFormat = 6;


|name|type|description|
|----|----|-----------|
|version|int32|The version number for the packet, usually 1.|
|sourceId|string|The end-to-end source of the packet. This is where any response should be directed to|
|targetId|string|The end-to-end target of the packet. This will become the source for any reply|
|command|string|A 4 character command code, identifying the main type of the packet|
|commandId|string|An optional GUID for the command, used to group the responses for a command together|
|dataFormat|string|The name of the data format inside the payload|

## sourceId and targetId addresses

Source ids and target ids can hold the following types of address:

* A ServerID
* A ClientID
* A ClusterID
* A resource ID
* The empty string

### Server ids

A server id has the format _servername@clustername_. The cluster name represents a group of servers that all
serve the same domain. Clients usually address their requests to a cluster, not to a specific server. They 
only address a specific server if they answer a command from a server.

### Resource ID

A client connects to some "organisation" in cluster using _orgID#cluster_.

### Cluster ids

A cluster ID is just a string obeying the rules for a Java identifier.

### Client ids

Client ids can have any format. They can have structure implied to them because the cluster domain 
imposes that, but the hub server does not care. Client IDs need to be globally unique or trouble
ensues. The hub detects duplicate client ids and prevents both clients from working when it occurs.

### The empty string

The empty string as an ID means that the packet is addressed to the hub itself, and is not meant
to be forwarded. These packets contain commands or responses from the hub to an individual connected
party.

## The data format field

The data format field defines the format for the payload section that follows. It can contain the following
values:

|Value|Description|
|-----|-----------|
|octet-stream|The data stream is a stream of bytes. Whatever is to be done with them is defined by the command. This format is typically used to transfer files|
|JSON:(class)|The data contains a JSON packet. The class name for the packet, needed to decode it, is the (class) in the string.|
|(empty)|No body is present|


# Authenticating and identifying connecting parties

Both clients and servers are responsible for initiating a connection. Whether a connecting party is a client
or a server is determined by how they respond during the authentication exchange that happens immediately
after a connection.

## Server to hub authentication

When a server connects to a hub the hub needs to ensure that this server is allowed to be used. The authentication
mechanism works as follows.

1. When the server connects the hub sends an HELO packet containing a multi-byte random challenge string.

2. The server uses the cluster password and encodes it with the challenge string into an sha256 hash and
sends it back with a ServerHeloResponse packet in the envelope. The fact that a ServerHeloResponse follows
on a HELO packet identifies the party as a server.

3. The hub checks the hash, and if correct enables the server for traffic. It gets registered with
its cluster, and any old registrations get dropped.

4. If incorrect, the hub responds with an exception packet and disconnects the connection.

## Client to hub authentication

Client to hub authentication is a bit more complex, because a client needs to be authorized by a server. The
basic process is similar to server authentication though..

1. When the client connects the hub sends an HELO packet containing a multi-byte random challenge string.

2. The client uses its user credentials and encodes its credentials with the challenge into a sha256 hash.
This hash is then returned in an envelope with the ClientHeloResponse. This identifies the connecting
party as a client.

3. The server forwards the HELO response to any server inside the cluster. If no servers are available then the
hub responds with an error stating so, and disconnects the client. The client is supposed to retry its connection
regularly.

4. The server receives the HELO packet with a ClientHeloResponse and validates the challenge with the sha hash. If
the data are correct it responds with an AUTH packet containing the client address. If authentication fails it 
responds with an error packet.

5. If the hub receives an error packet it removes the client from the client map and sends the client the error. 
It then disconnects the client.

6. If the response is an AUTH packet it accepts the client and registers it in the client map.

## Server/client identity during authentication

When a client or a server is in the process of authenticating we are not sure that they are whom they
say they are. And it might be that an authenticating party is already in our client or server maps. We
cannot assume at authentication time that the new connection will properly supersede the existing one, because
that would open the door to a DOS attack.

During authentication we keep the authenticating party in a separate table. We know we must use that table
simply _because_ we are authenticating.


# Error packets

We have two kinds of errors. There are /application level errors/ where a command failed to execute somewhere.
We also have errors in the communicatino between hub and party.

## Hub errors

When the hub does not agree on something that happened it will usually send an error packet. This is a packet
with in the envelope an ErrorResponse. The rest of the fields in the envelope identify the command and target
of the error.

The ErrorResponse has a fixed response code which can be used to correcly identify the kind of problem that
occurred so that appropriate action can be taken. For instance a client receiving an authentication error can
just stop connecting because it will not be able to continue.

Usually any error response by the hub will immediately be followed by the hub disconnecting the connection.

## Application errors

An application error is usually some command that failed in some way. This has no effect on the communication
layer. The response is usually a JSON ExceptionPacket. This packet is just transparantly delivered to the
target without the hub caring.






