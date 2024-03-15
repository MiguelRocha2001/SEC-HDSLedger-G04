package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class GetBalanceRequestSucessResultMessage extends Message {

    private String AcountOwnerId;
    private int balance;

    public GetBalanceRequestSucessResultMessage(String senderId, int balance, String acountOwnerId) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
        this.AcountOwnerId = acountOwnerId;
    }

    public int getBalance() { return balance; }

    public String getAcountOwnerId() { return AcountOwnerId; }
    
}
