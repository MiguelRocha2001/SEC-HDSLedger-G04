package pt.ulisboa.tecnico.hdsledger.communication;

public class ConsensusDecidedMessage extends Message {

    public ConsensusResultMessage(String senderId) {
        super(senderId, Type.VALUE_DECIDED);
    }
}
