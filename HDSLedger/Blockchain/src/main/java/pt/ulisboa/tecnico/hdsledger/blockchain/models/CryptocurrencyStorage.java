package pt.ulisboa.tecnico.hdsledger.blockchain.models;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;

import pt.ulisboa.tecnico.hdsledger.utilities.ErrorMessage;
import pt.ulisboa.tecnico.hdsledger.utilities.HDSSException;
import pt.ulisboa.tecnico.hdsledger.utilities.Pair;

public class CryptocurrencyStorage {
    private final Map<String, Integer> accounts = new HashMap<>();

    public class InvalidAccountException extends RuntimeException {}
    public class InvalidAmmountException extends RuntimeException {}

    /**
     * Initiates all account balances with 0 units.
     * @param nodeIds account ids
     */
    public CryptocurrencyStorage(String[] nodeIds) {
        for (int u = 0; u < nodeIds.length; u++) {
            accounts.put(nodeIds[u], 0);
        }
    }

    public int getBalance(String clientId) {
        for (Map.Entry<String, Integer> entry : accounts.entrySet()) {
            if (entry.getKey().equals(clientId))
                return entry.getValue();
        }
        throw new InvalidAccountException();
    }

    public void transfer(String sourceId, String destinationId, int amount) {
        int balance = getBalance(sourceId);
        if (balance >= amount) {
            getBalance(destinationId);
        } else
            throw new InvalidAmmountException();
    }
}
