package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestResultMessage extends Message {

    private String appendedValue;
    private int blockIndex;

    public AppendRequestResultMessage(String senderId, int blockIndex, String appendedValue) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.blockIndex = blockIndex;
        this.appendedValue = appendedValue;
    }

    public String getAppendedValue() { return appendedValue; }
    public int getBlockIndex() { return blockIndex; }
    
}
