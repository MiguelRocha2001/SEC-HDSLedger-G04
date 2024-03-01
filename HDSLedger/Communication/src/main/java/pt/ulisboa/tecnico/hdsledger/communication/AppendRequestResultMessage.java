package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class AppendRequestResultMessage extends Message {

    public AppendRequestResultMessage(String senderId) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
    }
    
}
