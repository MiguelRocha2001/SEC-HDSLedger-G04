package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage {

    // Value
    private TransactionV2 value;

    public CommitMessage(TransactionV2 value) {
        this.value = value;
    }

    public TransactionV2 getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
