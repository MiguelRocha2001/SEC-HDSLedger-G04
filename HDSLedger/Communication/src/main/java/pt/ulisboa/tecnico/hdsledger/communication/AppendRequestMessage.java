package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestMessage extends Message {

    private String message;
    private String valueSignature;

    public AppendRequestMessage(String senderId, String message, String valueSignature) {
        super(senderId, Type.APPEND_REQUEST);
        this.message = message;
        this.valueSignature = valueSignature;

    }

    public String getMessage() {
        return message;
    }

    public String getValueSignature() {
        return valueSignature;
    }
}
