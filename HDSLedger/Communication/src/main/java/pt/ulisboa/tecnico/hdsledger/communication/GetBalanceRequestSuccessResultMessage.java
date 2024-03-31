package pt.ulisboa.tecnico.hdsledger.communication;

import java.util.UUID;

import com.google.gson.Gson;

import pt.ulisboa.tecnico.hdsledger.communication.cripto.RSAKeyGenerator;

public class GetBalanceRequestSuccessResultMessage extends Message {

    private int balance;
    private UUID uuid;

    public GetBalanceRequestSuccessResultMessage(String senderId, int balance, UUID uuid) {
        super(senderId, Type.APPEND_REQUEST_RESULT);
        this.balance = balance;
        this.uuid = uuid;
    }

    public int getBalance() { return balance; }

    public String tojson() {
        return new Gson().toJson(this);
    }

    public UUID getUuid() {
        return uuid;
    }
}
