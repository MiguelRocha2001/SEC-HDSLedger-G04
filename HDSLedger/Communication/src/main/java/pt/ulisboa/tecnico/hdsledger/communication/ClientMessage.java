package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class ClientMessage extends Message {

    private String message;

    public ClientMessage(String senderId, Type type) {
        super(senderId, type);
    }

    public AppendRequestMessage deserializeAppendRequestMessage() {
        return new Gson().fromJson(this.message, AppendRequestMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
