package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class LeaderChangeMessage extends Message {

    private String leaderProcessId;
    private String valueToBeAppended;

    public LeaderChangeMessage(String senderId, String leaderProcessId, String valueToBeAppended) {
        super(senderId, Type.CONSENSUS_START);
        this.leaderProcessId = leaderProcessId;
        this.valueToBeAppended = valueToBeAppended;
    }

    public AppendRequestMessage deserializeAppendRequestMessage() {
        return new Gson().fromJson(this.leaderProcessId, AppendRequestMessage.class);
    }

    public String getLeaderProcessId() {
        return leaderProcessId;
    }

    public String getValueToBeAppended() {
        return valueToBeAppended;
    }
    
}
