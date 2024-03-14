package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class GetBalanceRequestSucessResultMessage extends Message {

    private int balance;

    public GetBalanceRequestSucessResultMessage(String senderId, int balance) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
    }

    public int getBalance() { return balance; }
    
}
