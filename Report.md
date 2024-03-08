# Architecture

## Components
The system is divided into two main components:
- The Client application;
- The Node server.

### Client Application
This is the application that the user uses to access/interact with the system. 

#### Config files
The application starts, and loads the configuration files:
- Client configuration file;
- Nodes configuration file.

We assumed that the 

#### Main loop
After initialization, the app stays in a continuous loop, reading user input. For this first phase, only the append operation is required and implemented, and so, the read input is used as the value for the append operation. The exception is the "exit" word, that terminates the application.

#### Client Service
The Client application needs to contact with the Blockchain nodes. To hide this complexity, the client application interacts with the Client service, which listens for messages coming from the Blockchain nodes. Also, it makes available, for this stage, the only possible operation: appendValue, which, again, for this stage, is just made by broadcasting to all nodes in the blockchain, requesting to append a string value.

Since we are using a Socket based Link, whenever a process sends a message to the other one, it is not required for him to deliver a response. This is handy because the append-request don't require an immediate response. In our implementation, the responses, in particular, the append-request response is arrived asynchronously, and only when the request is fulfilled. Also, this way, the client is not blocked waiting for a response, and could even make another request in the meantime.

Finally, whenever a new message arrives, a new thread is created to process the message, not causing a block in the Client application. At the moment, this might not be very useful since each message event shouldn't take much time to be processed.

Future plans: for the second stage, it is planned to implement the Client library/service as another separate component, and not as part of the Client application. This is not relevant for the security and functioning of the system, but only as a good design choice because, in the future, the Client service could be changed for another implementation for any porpose, and it becomes much easier to do so, if the library is designed as a separate and independent component of the Client application.

### Blockchain
This application is the second one that composes the system, and is the one that handles all the logic associated to the blockchain.

#### Node

##### Initialization
Again, similarly to the client, the application starts of by loading the necessary configuration files. In particular, besides reading all the nodes, the application also reads all the clients. This was not made just for simplicity. It makes sense, since, in a real system, a user would have to be registered as a Client, so the nodes could have all the information to contact the client. However, currently, and for simplicity, the client hostname and port are also specified in the configuration file.

After initialization, the Node spawns two services:
- Blockchain Service;
- Node Service.

#### Blockchain Service
This service offers a simple API for the Clients to request services from the Blockchain system.
Similar to the Client application, this service spawns a thread listening for new request, coming from the Clients. This is important because this service uses a single Link for communication, which should be created for communication with only the clients, not having to deal with any communication between other nodes.

For this first stage, this service only handles append-request's, which means start a new consensus instance, in hopes of deciding the clients request value. Since this might not happen, the request is stored and, whenever the consensus instance is over, a new consensus instance is started with a value that was not yet decided.

#### Node Service
This service holds the logic associated to the consensus algorithm. 

The communication is mostly done between nodes, and the only time the clients are contacted is to inform that it's own value was decided/appended to the blockchain. Also, the communication is done the same way as in the Blockchain Service.

The consensus algorithm implemented is the IBFT protocol, which aims to decide a value, having in mind possible byzantine nodes. The normal case operation (pre-prepare, prepare and commit phases) was already implemented previously, by another group. Yet, they are extended to allow the node to simulate byzantine behaviors. <TALK ABOUT THIS EXTENSIONS>

Besides the normal case implementation, the View-Change was also implemented, allowing for progression/liveness whenever a node is not correct. The implementation follows the algorithm described in the IBFT paper, and thus, the details of it's implementation are not relevant here.


### Communication
To support communication between processes, (Client <-> Node) and (Node <-> Node), the Link (Link.java) layer was used, since it was already provided and mostly implemented. This service uses UDP, to closely simulate a Fair Loss link. 

Some abstractions/layers were already implemented, such as the use of ACKs to enforce validity, and the detection of duplicated messaged, using message IDs. Therefore, to implement a perfect authenticated Link, the authentication using digital signatures was implemented <WHY IS THIS NEEDED>? MACs could also be used to ensure integrity, but they don't provide non repudiation. Yet, as a future task, we need to access if we really need non repudiation and just guarantee of integrity.

In practice, the original message is signed and the signature is appended to the end of the message, to be sent with it. In the receiver, the signature is extracted and verified against the original message. The signature size is always the same, and thus, it is easy to make the separation on the receiver side.

### Leader Selection
The IBFT protocol doesn't specify a way to choose the current leader. It only says that the algorithm must give a chance for every node to become a leader. Therefore, we choose to select the leader based on the node configuration file, which is read the same way by every node. The first node will start as the leader, and, for every round, the leader avances for the next node, based on the configuration file order.

# Dependability
With the IBFT protocol, safety is ensured across rounds. This is duo to the justification mechanisms that ensures that if a new leader (duo to a round change) proposes a value, and a value was already decided in a previous round by another previous leader, then, this value will be decided by any new leader.

Confidentiality is not implemented for this stage, for simplicity, since it is not required for the well functioning of the blockchain.

Integrity is ensured with the use of digital signatures, not allowing byzantine processes to change any message content. The main threat is if the byzantine process relays an authenticated message that was sent by a correct process in a previous instance/round. Yet, this wont make much harm since that message refers only to a previous instance.

# Bizantine Behavior
To prove that this system is resilient to several byzantine atacks, the system was tested with multiple configurations, each one corresponding to a different set of processes and respective behaviors:

- correctConfig.json: all nodes are correct, and the system don't face any atacks;

- ignoreRequestsConfig.json: a single node will ignore the requests coming towards him. In particular, this one will start as leader (id = 1). The tests will show that, after all nodes start consensus, the other nodes will get their timers expired, and they will change their view, so the node with id = 2 will become the new leader, and, since it is correct, it will lead the consensus to a decision/conclusion.

- badLeaderPropose.json: the leader (byzantine) will propose a random value for each one of the nodes. The output will show that no node will ever receive a quorum of identical PREPARE-MESSAGE's, leading to a view change, where the original client value will finally be decided.

- uponPrepareQuorumWrongValue.json: shows that if a byzantine process, after receiving a quorum of PREPARE requests, and decides to broadcast a COMMIT message, with a incoerent value, the consensus will still decide the right value.

- uponRoundChangeQuorumWrongValue.json: similar to the above test, the byzantine and leader node will set a wrong value after receiving a quorum of ROUND-CHANGE messages.

There is a byzantine behavior that we still dont know how to protect against. If the leader is byzantine, and the client wants to append the value X, the byzantine leader could receive the request but never propose that value, and instead propose another one, and the other nodes would never suspect about it, and, so, no view change would be triggered.

Note: the system is only capable of garantee resilience if the maximum number of byzantine nodes is f and N = 3f, at least, N being the total number of nodes.