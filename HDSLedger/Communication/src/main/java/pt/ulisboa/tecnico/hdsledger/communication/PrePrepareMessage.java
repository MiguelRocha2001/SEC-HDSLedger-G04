package pt.ulisboa.tecnico.hdsledger.communication;

import com.google.gson.Gson;

public class PrePrepareMessage {
    
    // Value
    private String value;
    private String valueSignature;

    public PrePrepareMessage(String value, String valueSignature) {
        this.value = value;
        this.valueSignature = valueSignature;
    }

    public String getValue() {
        return value;
    }

    public String toJson() {
        return new Gson().toJson(this);
    }

    public String getValueSignature() {
        return valueSignature;
    }
}   
