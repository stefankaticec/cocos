# Packet protocol

## Basic package format

All packets between hub and client have the same format. The general format is as follows:

|Size|Type|Value|Description|
|------|----|-----|-----------|
|4|int32|0xdaeda105|A fixed value that indicates packet start, to ward against sync faults|
|4|int32|(length h)|The length, in bytes, of the packet header that follows
|h|protobuf envelope|-|A protobuf with a fixed format which is the envelope of the command. This protobuf has a fixed format, but is an union type.
|4|int32|(length p)|The length of the payload: the rest of the data.|
|p|byte[]|user defined|The payload of the packet. The format of the payload is determined by information in the header. The payload is only decoded in the endpoints; the hub just streams it to whatever destination as a byte stream|

The packet envelope has the following types:






