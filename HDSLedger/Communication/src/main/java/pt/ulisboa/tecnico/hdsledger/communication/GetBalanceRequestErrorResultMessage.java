package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class GetBalanceRequestErrorResultMessage extends Message {

    public GetBalanceRequestErrorResultMessage(String senderId) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
    }
}
