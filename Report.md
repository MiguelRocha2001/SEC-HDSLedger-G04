# Architecture

## Components
The system is diveded into two main components:
- The Client application;
- The Node server.

### Client Application
This is the application that the user uses to access/interact with the system. 

#### Comfig files
The application starts, and loads the configuration files:
- Client configuration file;
- Nodes configuration file.

We assumed that the 

#### Main loop
After initialization, the app stays in a continuous loop, reading user input. For this first phase, only the append operation is required and implemented, and so, the read input is used as the value for the append operation. The exception is the "exit" word, that terminates the application.

#### Client Service
The Client application needs to contact with the Blockchain nodes. To hide this complexity, the client application interacts with the Client service, which listens for messages coming from the Blockchain nodes. Also, it makes available, for this stage, the only possible operation: appendValue, which, again, for this stage, is just made by broadcasting to all nodes in the blockchain, requesting to append a string value.

Since we are using a Socket based Link, whenever a process sends a message to the other one, it is not required for him to deliver a response. This is handy because the append-request dont require an imediate response. In our implementation, the responses, in particular, the append-request response is arrived assycronously, and only when the request is fullfiled. Also, this way, the client is not blocked whaiting for a response, and could even make another request in the meantime.

Finally, whenever a new mesage arrives, a new thread is created to process the message, not causing a block in the Client application. At the moment, this might not be very usefull since each message event shouldn't take much time to be processed.

Future plans: for the second stage, it is planned to implement the Client library/service as another separate component, and not as part of the Client application. This is not relevant for the security and functioning of the system, but only as a good design choice because, in the future, the Client service could be changed for another implementation for any porpose, and it becomes much easier to do so, if the library is designed as a separate and independent component of the Client application.

### Blockchain
This application is the second one that composes the system, and is the one that handles all the logic accossiated to the blockchain.

#### Node

##### Initialization
Again, similary to the client, the application starts of by loading the necessary configuartion files. In particular, besides reading all the nodes, the application also reads all the clients. This was not made just for simmplicity. It makes sense, since, in a real system, a user would have to be registered as a Client, so the nodes could have all the information to contact the client. However, currently, and for simplicity, the client hostname and port are also specified in the configuration file, which doesnt make much sense, since the clients should have the hability to interact with the system throught any place. Therefore, it is planned to send the hostname and port in each message to the nodes, so the latter can have a way to deliver a response.

After initialization, the Node spawns two services:
- Blockchain Service;
- Node Service.

#### Blockchain Service
This service offers a simple API for the Clients to request services from the Blockchain system.
Similar to the Client application, this service spawns a thread listening for new request, coming from the Clients. This is important because this service uses a single Link for comunication, which should be created for comunication with only the clients, not having to deal with any comunication between other nodes.

For this first stage, this service only handles append-request's, which means start a new consensus instance, in hopes of deciding the clients request value. Since this might not happen, the request is stored and, whenever the consensus instance is over, a new consensus instance is started with a value that was not yet decided.

#### Node Service
This service holds the logic acossiated to the consensus algorithm. 

The comunication is mostly done between nodes, and the only time the clients are contacted is to infom that it's own value was decided/appended to the blockchain. Also, tthe comunication is done the same way as in the Blockchain Service.

The consensus algorithm implemented is the IBFT protocol, which aims to decide a value, having in mind possible bizantine nodes. The normal case operation (pre-prepare, prepare and commit phases) was already implemented previously, by another group. Yet, they are extended to allow the node to simulate byzantine behaviours. TALK ABOUT THIS EXTENSIONS

Besides the normal case implementation, the View-Change was also implemented, allowing for progression/liveness whenever a node is not correct. The implementation follows the algorithm described in the IBFT paper, and thus, the details of it's implementation are not relevant here.


### Link layer
