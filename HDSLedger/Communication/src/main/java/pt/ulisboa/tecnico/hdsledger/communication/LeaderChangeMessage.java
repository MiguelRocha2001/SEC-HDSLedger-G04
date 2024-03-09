package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class LeaderChangeMessage extends Message {

    private String leaderId;

    public LeaderChangeMessage(String senderId, String newLeaderId) {
        super(senderId, Type.CONSENSUS_START);
        leaderId = newLeaderId;
    }

    public String getLeaderId() {
        return leaderId;
    }
}
