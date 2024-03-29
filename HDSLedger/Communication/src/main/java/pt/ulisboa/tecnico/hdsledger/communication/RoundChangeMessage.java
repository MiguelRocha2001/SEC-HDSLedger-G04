package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class RoundChangeMessage {

    private Block preparedValue;
    private int preparedRound;

    public RoundChangeMessage(Block preparedValue, int preparedRound) {
        this.preparedValue = preparedValue;
        this.preparedRound = preparedRound;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public Block getPreparedValue() { return preparedValue; }

    public int getPreparedRound() { return preparedRound; }
}
