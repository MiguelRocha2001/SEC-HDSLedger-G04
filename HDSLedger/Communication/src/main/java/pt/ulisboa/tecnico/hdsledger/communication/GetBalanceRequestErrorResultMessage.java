package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class GetBalanceRequestErrorResultMessage extends Message {

    private String AcountOwnerId;
    
    public GetBalanceRequestErrorResultMessage(String senderId, String acountOwnerId) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.AcountOwnerId = acountOwnerId;
    }

    public String getAcountOwnerId() { return AcountOwnerId; }
}
