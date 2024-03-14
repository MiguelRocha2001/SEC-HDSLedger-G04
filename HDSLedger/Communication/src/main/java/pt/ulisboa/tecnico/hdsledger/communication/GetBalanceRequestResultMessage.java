package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class GetBalanceRequestResultMessage extends Message {

    private int balance;

    public GetBalanceRequestResultMessage(String senderId, int balance) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
    }

    public int getBalance() { return balance; }
    
}
