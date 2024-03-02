package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestResultMessage extends Message {

    private int blockIndex;

    public AppendRequestResultMessage(String senderId, int blockIndex) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.blockIndex = blockIndex;
    }

    public int getBlockIndex() { return blockIndex; }
    
}
