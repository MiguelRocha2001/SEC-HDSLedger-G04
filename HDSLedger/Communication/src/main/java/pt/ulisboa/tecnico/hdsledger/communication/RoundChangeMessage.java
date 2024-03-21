package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    private TransactionV2 preparedValue;
    private int preparedRound;

    public RoundChangeMessage(TransactionV2 preparedValue, int preparedRound) {
        this.preparedValue = preparedValue;
        this.preparedRound = preparedRound;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public TransactionV2 getPreparedValue() { return preparedValue; }

    public int getPreparedRound() { return preparedRound; }
}
