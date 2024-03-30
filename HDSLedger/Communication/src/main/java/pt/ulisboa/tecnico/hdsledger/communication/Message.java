package pt.ulisboa.tecnico.hdsledger.communication;

import java.io.Serializable;

public class Message implements Serializable {

    // Sender identifier
    private String senderId;
    // Message identifier
    private int messageId;
    // Message type
    private Type type;

    public enum Type {
        APPEND_REQUEST, APPEND_REQUEST_RESULT, LIDER_CHANGE, GET_BALANCE, TRANSFER, GET_BALANCE_SUCCESS_RESULT, GET_BALANCE_ERROR_RESULT, TRANSFER_SUCCESS_RESULT, TRANSFER_ERROR_RESULT, // Client - Server
        VALUE_DECIDED, CONSENSUS_START, // Consensus - Blockchain
        APPEND, PRE_PREPARE, PREPARE, COMMIT, ROUND_CHANGE, // Consensus - Consensus
        ACK, IGNORE
    }

    public Message(String senderId, Type type) {
        this.senderId = senderId;
        this.type = type;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }
}
