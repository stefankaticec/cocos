# Command handling

Commands are sent from server to client. A command has the following data associated with it:

* The command ID. This is a GUID that uniquely identifies the command.

* The command name: a name that is interpretable by both sides.

* A data format: the name of the data format that is in the BODY part of a packet.

* A command flags field, indicating what kind of command packet this is.

* A response sequence field, which numbers the response packets associated with the command sequentially.

* A parameters field which is part of the envelope and can contain extra data concerning the command.

## Description of all command fields

### The command ID

The command ID is a unique identifier for a command. All packets related to a single command share
the same command ID. The command ID is used to group all packets for a given command instance together.
The command id gets assigned by the sender using a GUID.

### The command name

The command name is a string containing a command name. The command name can be any kind of string, but
the server and the client need to agree on the meaning of the string. The Hub never interprets command names
but just echoes them. The command name is always repeated in all packets related to the command.

### The data format

The data format describes the format of the data inside the packet body. If there is no body then
the data format is the empty string. The data format JSON is used to specify that the packet body
contains a JSON structure; in this case the command name usually contains the name of the Java class that
represents that structure so that it can be unmarshalled.

### The response sequence field

When a client handles a command it can send one or more responses. Each response is strictly ordered, 
so that responses are always numbered in the order they were generated. The response sequence field
starts at 0.

### The parameters field

This field gets used for binary packets. It can contain JSON formatted data.


## Command protocol

The handling of a command can follow the following paths.

### Successful command without console output

* The command is sent to the client.

* The client executes the command. While it is running it will send COMMAND_PROGRESS packets at least once a 
minute. These COMMAND_PROGRESS packages show the percentage complete and any task path.

* Once the command completes it sends a COMMAND_COMPLETED packet with any result encoded in the body.

Once the COMMAND_COMPLETED packet is received the server removes the command from its queues as it is finished.

### Unsuccessful command without console output

* The command is sent to the client.

* The client refuses the command and replies with a COMMAND_ERROR packet. It can send 
some COMMAND_PROGRESS packets in between.

The server finishes off the command and reports its failure from the error packet.

### Console output

Console output can be sent by a command using COMMAND_OUTPUT packets. These packets are sent as soon
as data becomes available and their content should be concatenated together to get the complete output
for a command.

## Commands and communication errors

### Client not present

If the server sends a command to a client that is not present then the server immediately receives a COMMAND_ERROR
packet, with the code indicating that the client is not present.

### Client does not understand the command

If a client does not understand/accept a command it will send back an ERROR packet which is forwarded to the
server.

### Client cannot send a packet to the hub

If the client cannot send a packet to the hub it stores the packet until the hub becomes available 
again. If more than 10 packets are kept in the send queue the sending process inside the client will
remain blocked on the send action, so that any process creating output waits until the hub is again
available.

### Hub cannot send a packet to a server

A packet received from a client needs to be forwarded to its server. If the hub is unable to send that packet,
for instance because the server is currently unavailable or because there was an error sending the packet it
will retain the packet internally until the server returns.
If the amount of retained packets becomes too large for a server the hub will start to refuse packets,
which should cause the clients to keep their data until the server becomes available again.


 










