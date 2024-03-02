package pt.ulisboa.tecnico.hdsledger.communication;

public class ConsensusDecidedMessage extends Message {

    private String appendedValue;
    private int blockIndex;

    public ConsensusDecidedMessage(String senderId, String appendedValue, int blockIndex) {
        super(senderId, Type.VALUE_DECIDED);
        this.appendedValue = appendedValue;
        this.blockIndex = blockIndex;
    }

    public String getAppendedValue() { return appendedValue; }

    public int getBlockIndex() { return blockIndex; }
}
