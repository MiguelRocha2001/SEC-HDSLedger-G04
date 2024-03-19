package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import com.google.gson.Gson;

public class GetBalanceRequestErrorResultMessage extends Message {

    private String errorMessage;
    private PublicKey requestedPublickey;
    
    public GetBalanceRequestErrorResultMessage(String senderId, String errorMessage, PublicKey requestedPublickey) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.errorMessage = errorMessage;
        this.requestedPublickey = requestedPublickey;
    }

    public String getErrorMessage() { return errorMessage; }
    public PublicKey getRequestedPublickey() { return requestedPublickey; }
}