package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestMessage extends Message {

    private String message;

    public AppendRequestMessage(String senderId, String message) {
        super(senderId, Type.APPEND_REQUEST);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
