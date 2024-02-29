package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestMessage extends Message {

    private String message;

    public AppendRequestMessage(String senderId, String message) {
        super(senderId, Type.APPEND_REQUEST);
        this.message = message;
    }

    public AppendRequestMessage deserializeAppendRequestMessage() {
        return new Gson().fromJson(this.message, AppendRequestMessage.class);
    }

    public String getMessage() {
        return message;
    }
}
