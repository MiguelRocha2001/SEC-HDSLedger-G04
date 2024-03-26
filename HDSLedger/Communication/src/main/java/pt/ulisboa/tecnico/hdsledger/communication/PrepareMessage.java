package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrepareMessage {
    
    // Value
    private TransactionBlock value;

    public PrepareMessage(TransactionBlock value) {
        this.value = value;
    }

    public TransactionBlock getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}   
