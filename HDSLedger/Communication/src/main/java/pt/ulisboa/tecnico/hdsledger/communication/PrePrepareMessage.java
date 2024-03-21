package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Value
    private TransactionV2 value;
    private byte[] valueSignature;

    public PrePrepareMessage(TransactionV2 value, byte[] valueSignature) {
        this.value = value;
        this.valueSignature = valueSignature;
    }

    public TransactionV2 getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public byte[] getValueSignature() {
        return valueSignature;
    }
}   
