package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class StartConsensusMessage extends Message {

    private String message;

    public StartConsensusMessage(String senderId, String message) {
        super(senderId, Type.CONSENSUS_START);
        this.message = message;
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
