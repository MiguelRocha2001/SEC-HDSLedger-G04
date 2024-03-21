package pt.ulisboa.tecnico.hdsledger.communication;

public class AppendRequestMessage extends Message {

    private String message;
    private byte[] valueSignature;

    public AppendRequestMessage(String senderId, String message, byte[] valueSignature) {
        super(senderId, Type.APPEND_REQUEST);
        this.message = message;
        this.valueSignature = valueSignature;
    }

    public String getMessage() {
        return message;
    }

    public byte[] getValueSignature() {
        return valueSignature;
    }
}
