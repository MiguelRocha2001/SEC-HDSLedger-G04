package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class CommitMessage {

    // Value
    private Block value;

    public CommitMessage(Block value) {
        this.value = value;
    }

    public Block getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }
}
