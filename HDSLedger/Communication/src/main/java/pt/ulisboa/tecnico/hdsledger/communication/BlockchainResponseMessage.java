package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class BlockchainResponseMessage extends Message {
    
    // Serialized request
    private String message;

    public BlockchainResponseMessage(String senderId, Type type, String message) {
        super(senderId, type);
        this.message = message;
    }

    public AppendRequestResultMessage deserializeAppendRequestResultMessage() {
        return new Gson().fromJson(message, AppendRequestResultMessage.class);
    }

    public LeaderChangeMessage deserializeLeaderChangeMessage() {
        return new Gson().fromJson(message, LeaderChangeMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
