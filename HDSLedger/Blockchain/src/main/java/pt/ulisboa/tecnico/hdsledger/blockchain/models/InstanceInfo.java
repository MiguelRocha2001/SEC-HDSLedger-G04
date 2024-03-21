package pt.ulisboa.tecnico.hdsledger.blockchain.models;


import pt.ulisboa.tecnico.hdsledger.communication.CommitMessage;
import java.util.Timer;
import java.util.TimerTask;

public class InstanceInfo {

    private int currentRound = 1;
    private int preparedRound = -1;
    private TransactionV2 preparedValue;
    private CommitMessage commitMessage;
    private TransactionV2 inputValue;
    private int committedRound = -1;
    private String leaderId;
    private byte[] valueSignature;
    private Timer timer;

    /*
    public InstanceInfo(TransactionV2 inputValue, String valueSignature) {
        this.inputValue = inputValue;
        this.valueSignature = valueSignature;
    }
    */

    public InstanceInfo(TransactionV2 inputValue, byte[] helloSignature, String leaderId) {
        this.inputValue = inputValue;
        this.leaderId = leaderId;
        this.valueSignature = helloSignature;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public void setCurrentRound(int currentRound) {
        this.currentRound = currentRound;
    }

    public int getPreparedRound() {
        return preparedRound;
    }

    public void setPreparedRound(int preparedRound) {
        this.preparedRound = preparedRound;
    }

    public TransactionV2 getPreparedValue() {
        return preparedValue;
    }

    public void setPreparedValue(TransactionV2 preparedValue) {
        this.preparedValue = preparedValue;
    }

    public TransactionV2 getInputValue() {
        return inputValue;
    }

    public void setInputValue(TransactionV2 inputValue) {
        this.inputValue = inputValue;
    }

    public int getCommittedRound() {
        return committedRound;
    }

    public void setCommittedRound(int committedRound) {
        this.committedRound = committedRound;
    }

    public CommitMessage getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(CommitMessage commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(String leaderId) {
        this.leaderId = leaderId;
    }

    public byte[] getValueSignature() {
        return valueSignature;
    }

    // TODO: this should be parameterized by the round. See this later...
    public void schedualeTask(TimerTask timerTask) {
        long TIMEOUT = 3000; // TODO: change to another place

        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = new Timer();    
        } else {
            timer = new Timer();
        }
        timer.schedule(timerTask, TIMEOUT); // set timer
    }

    public void cancelTimer() {
        timer.cancel();
    }
}
