package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.Message.Type;
import pt.ulisboa.tecnico.hdsledger.communication.cripto.CriptoUtils;
import pt.ulisboa.tecnico.hdsledger.utilities.*;

import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.net.*;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.LogManager;

public class Link {

    private static final CustomLogger LOGGER = new CustomLogger(Link.class.getName());
    // Time to wait for an ACK before resending the message
    private final int BASE_SLEEP_TIME;
    // UDP Socket
    private final DatagramSocket socket;
    // Map of all nodes in the network
    private final Map<String, ProcessConfig> nodes = new ConcurrentHashMap<>();
    // Reference to the node itself
    private final ProcessConfig config;
    // Set of received messages from specific node (prevent duplicates)
    private final Map<String, CollapsingSet> receivedMessages = new ConcurrentHashMap<>();
    // Set of received ACKs from specific node
    private final CollapsingSet receivedAcks = new CollapsingSet();
    // Class to deserialize messages to
    private final Class<? extends Message> messageClass;
    // Message counter
    private final AtomicInteger messageCounter = new AtomicInteger(0);
    // Send messages to self by pushing to queue instead of through the network
    private final Queue<Message> localhostQueue = new ConcurrentLinkedQueue<>();
    private CriptoUtils cripto;

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes, Class<? extends Message> messageClass, CriptoUtils criptoUtils) {
        this(self, port, nodes, false, 200, messageClass, criptoUtils);
    }

    public Link(ProcessConfig self, int port, ProcessConfig[] nodes,
            boolean activateLogs, int baseSleepTime, Class<? extends Message> messageClass, CriptoUtils criptoUtils) {

        this.config = self;
        this.BASE_SLEEP_TIME = baseSleepTime;
        this.messageClass = messageClass;
        this.cripto = criptoUtils;

        Arrays.stream(nodes).forEach(node -> {
            String id = node.getId();
            this.nodes.put(id, node);
            receivedMessages.put(id, new CollapsingSet());
        });

        try {
            this.socket = new DatagramSocket(port, InetAddress.getByName(config.getHostname()));
        } catch (UnknownHostException | SocketException e) {
            System.out.println(e.getMessage());
            throw new HDSSException(ErrorMessage.CannotOpenSocket);
        }
        
        if (!activateLogs) {
            LogManager.getLogManager().reset();
        }
    }

    public void ackAll(List<Integer> messageIds) {
        receivedAcks.addAll(messageIds);
    }

    /*
     * Broadcasts a message to all nodes in the network
     *
     * @param data The message to be broadcasted
     */
    public void broadcast(Message data) {
        Gson gson = new Gson();
        nodes.forEach((destId, dest) -> send(destId, gson.fromJson(gson.toJson(data), data.getClass())));
    }

    public void sendToRandom(Message data) {
        send("0", data); // TODO: change to random
    }

    /*
     * Sends a message to a specific node with guarantee of delivery
     *
     * @param nodeId The node identifier
     *
     * @param data The message to be sent
     */
    public void send(String nodeId, Message data) {

        // Spawn a new thread to send the message
        // To avoid blocking while waiting for ACK
        new Thread(() -> {
            try {
                ProcessConfig node = nodes.get(nodeId);
                if (node == null) {
                    LOGGER.log(Level.WARNING,
                            MessageFormat.format("{0} - Cant send a message to invalid node {1}",
                                    config.getId(), node));
                    throw new HDSSException(ErrorMessage.NoSuchNode);
                }

                data.setMessageId(messageCounter.getAndIncrement());

                // If the message is not ACK, it will be resent
                InetAddress destAddress = InetAddress.getByName(node.getHostname());
                int destPort = node.getPort();
                int count = 1;
                int messageId = data.getMessageId();
                int sleepTime = BASE_SLEEP_TIME;

                // Send message to local queue instead of using network if destination in self
                if (nodeId.equals(this.config.getId())) {
                    this.localhostQueue.add(data);

                    LOGGER.log(Level.INFO,
                            MessageFormat.format("{0} - Message {1} (locally) sent to {2}:{3} successfully",
                                    config.getId(), data.getType(), destAddress, destPort));

                    return;
                }

                for (;;) {
                    LOGGER.log(Level.INFO, MessageFormat.format(
                            "{0} - Sending {1} message to {2}:{3} with message ID {4} - Attempt #{5}", config.getId(),
                            data.getType(), destAddress, destPort, messageId, count++));
        
                    unreliableSend(destAddress, destPort, data);

                    // Wait (using exponential back-off), then look for ACK
                    Thread.sleep(sleepTime);

                    // Receive method will set receivedAcks when sees corresponding ACK
                    if (receivedAcks.contains(messageId))
                        break;

                    sleepTime <<= 1;
                }

                LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} sent to {2}:{3} successfully",
                        config.getId(), data.getType(), destAddress, destPort));
            } catch (InterruptedException | UnknownHostException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /*
     * Sends a message to a specific node without guarantee of delivery
     * Mainly used to send ACKs, if they are lost, the original message will be
     * resent
     *
     * @param address The address of the destination node
     *
     * @param port The port of the destination node
     *
     * @param data The message to be sent
     */
    public void unreliableSend(InetAddress hostname, int port, Message data) {
        new Thread(() -> {
            try {
                byte[] buf = new Gson().toJson(data).getBytes();
                byte[] buffSignedEncoded = cripto.addSignatureToDataAndEncode(buf, config.getId()); // encodes to Base 64
                
                DatagramPacket packet = new DatagramPacket(buffSignedEncoded, buffSignedEncoded.length, hostname, port);

                socket.send(packet);
            } catch (IOException e) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.SocketSendingError);
            } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException | InvalidKeySpecException e) {
                e.printStackTrace();
                // TODO: warn about digital signature error
                throw new HDSSException(ErrorMessage.SocketSendingError); // TODO: change later
            }
        }).start();
    }

    /*
    private Class<? extends Message> getMessageType(Message message) {
        Type type = message.getType();
        
        if (
            type == Type.APPEND | 
            type == Type.PRE_PREPARE | 
            type == Type.PREPARE | 
            type == Type.COMMIT
        ) {
            return ConsensusMessage.class;
        } if (type == Type.APPEND_REQUEST)
            return AppendRequestMessage.class;
        if (type == Type.APPEND_REQUEST_RESULT)
            return AppendRequestResultMessage.class;
        else
            return null; // TODO: maybe, throw an Exception
    }
    */

    /*
     * Receives a message from any node in the network (blocking)
     */
    public Message receive() throws IOException, ClassNotFoundException {

        byte[] signature = null;
        byte[] originalMessage = null;

        Message message = null;
        String serialized = "";
        Boolean local = false;
        DatagramPacket response = null;
        
        if (this.localhostQueue.size() > 0) {

            message = this.localhostQueue.poll();
            local = true;
            this.receivedAcks.add(message.getMessageId());
        } else {
            byte[] buf = new byte[65535];
            response = new DatagramPacket(buf, buf.length);

            socket.receive(response);

            byte[] buffer = Arrays.copyOfRange(response.getData(), 0, response.getLength());
            
            buffer = Base64.getDecoder().decode(buffer); // decodes from Base 64

            signature = CriptoUtils.extractSignature(buffer);
            originalMessage = CriptoUtils.removeMessage(buffer);

            serialized = new String(originalMessage);

            message = new Gson().fromJson(serialized, Message.class);
        }

        String senderId = message.getSenderId();
        int messageId = message.getMessageId();

        if (config.getByzantineBehavior() == ByzantineBehavior.DONT_VERIFY_SIGNATURES) {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Node is byzantine (DONT_VERIFY_SIGNATURES). Message {1} wont be verified",
                        config.getId(), message.getMessageId()));
        } else if (local == false) {
            try {
                boolean verifies = cripto.verifySignature(senderId, originalMessage, signature);
                if (!verifies) {
                    LOGGER.log(Level.WARNING, MessageFormat.format("{0} - Message {1} could not be verified",
                        config.getId(), message.getMessageId()));
                    message.setType(Message.Type.IGNORE);
                    //throw new HDSSException(ErrorMessage.MessageVerificationFail);
                } else {
                    //LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} from sender {2} verified",
                    //    config.getId(), message.getMessageId(), message.getSenderId()));

                    // Do nothing
                }
            } catch(
                IOException |
                NoSuchAlgorithmException |
                InvalidKeySpecException |
                InvalidKeyException |
                SignatureException e
            ) {
                e.printStackTrace();
                throw new HDSSException(ErrorMessage.ProgrammingError); // TODO: maybe other exception type ???
            }
        } else {
            LOGGER.log(Level.INFO, MessageFormat.format("{0} - Message {1} is local and wont be verified",
                        config.getId(), message.getMessageId()));
        }

        if (!nodes.containsKey(senderId)) {
            LOGGER.log(Level.WARNING,
                            MessageFormat.format("{0} - Cant receive message from invalid sender {1}",
                                    config.getId(), senderId));
            throw new HDSSException(ErrorMessage.NoSuchNode);
            //message.setType(Message.Type.IGNORE);
        }

        // Handle ACKS, since it's possible to receive multiple acks from the same
        // message
        if (message.getType().equals(Message.Type.ACK)) {
            receivedAcks.add(messageId);
            return message;
        }

        // It's not an ACK -> Deserialize for the correct type
        if (!local && message.getType() != Type.IGNORE) {
            //Class<? extends Message> type = getMessageType(message);
            message = new Gson().fromJson(serialized, this.messageClass);
        }

        boolean isRepeated = !receivedMessages.get(message.getSenderId()).add(messageId);
        Type originalType = message.getType();
        // Message already received (add returns false if already exists) => Discard
        if (isRepeated) {
            LOGGER.log(Level.WARNING, MessageFormat.format("{0} - Message {1} is repeated",
                        config.getId(), message.getMessageId()));
            message.setType(Message.Type.IGNORE);
        }

        switch (message.getType()) {
            case PRE_PREPARE -> {
                return message;
            }
            case IGNORE -> {
                if (!originalType.equals(Type.COMMIT))
                    return message; // this is weird because, IGNORE means that the message was already received,
                                    // and maybe we should send the ACK so the other process doesnt continue to
                                    // send us the same msg, that we well ignore continuously, since is repeated
            }
            case PREPARE -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());

                return message;
            }
            case COMMIT -> {
                ConsensusMessage consensusMessage = (ConsensusMessage) message;
                if (consensusMessage.getReplyTo() != null && consensusMessage.getReplyTo().equals(config.getId()))
                    receivedAcks.add(consensusMessage.getReplyToMessageId());
            }
            default -> {}
        }

        // send ACK
        if (!local) {
            InetAddress address = InetAddress.getByName(response.getAddress().getHostAddress());
            int port = response.getPort();

            Message responseMessage = new Message(this.config.getId(), Message.Type.ACK);
            responseMessage.setMessageId(messageId);

            // ACK is sent without needing for another ACK because
            // we're assuming an eventually synchronous network
            // Even if a node receives the message multiple times,
            // it will discard duplicates
            unreliableSend(address, port, responseMessage);
        }

        return message;
    }
}
