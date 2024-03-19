package pt.ulisboa.tecnico.hdsledger.communication;

import java.security.PublicKey;

import com.google.gson.Gson;

public class GetBalanceRequestSucessResultMessage extends Message {

    private PublicKey requestedPublickey;
    private int balance;

    public GetBalanceRequestSucessResultMessage(String senderId, int balance, PublicKey requestedPublickey) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
        this.requestedPublickey = requestedPublickey;
    }

    public int getBalance() { return balance; }
    public PublicKey getRequestedPublickey() { return requestedPublickey; }
}
