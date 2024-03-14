package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class BlockchainRequestMessage extends Message {
    
    // Serialized request
    private String message;

    public BlockchainRequestMessage(String senderId, Type type, String message) {
        super(senderId, type);
        this.message = message;
    }

    public AppendRequestMessage deserializeAppendRequest() {
        return new Gson().fromJson(message, AppendRequestMessage.class);
    }

    public GetBalanceRequestMessage deserializeGetBalanceRequest() {
        return new Gson().fromJson(message, GetBalanceRequestMessage.class);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
