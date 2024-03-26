package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    private TransactionBlock preparedValue;
    private int preparedRound;

    public RoundChangeMessage(TransactionBlock preparedValue, int preparedRound) {
        this.preparedValue = preparedValue;
        this.preparedRound = preparedRound;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public TransactionBlock getPreparedValue() { return preparedValue; }

    public int getPreparedRound() { return preparedRound; }
}
