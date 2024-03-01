package pt.ulisboa.tecnico.hdsledger.communication;

public class ConsensusDecidedMessage extends Message {

    private String clientId;

    public ConsensusDecidedMessage(String senderId, String clientId) {
        super(senderId, Type.VALUE_DECIDED);
        this.clientId = clientId;
    }

    public String getClientId() { return clientId; }
}
