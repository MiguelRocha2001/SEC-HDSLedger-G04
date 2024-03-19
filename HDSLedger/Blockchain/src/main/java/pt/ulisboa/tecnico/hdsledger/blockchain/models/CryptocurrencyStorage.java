package pt.ulisboa.tecnico.hdsledger.blockchain.models;

import java.util.HashMap;
import java.util.Map;

public class CryptocurrencyStorage {
    private final Map<String, Integer> accounts = new HashMap<>();

    public class InvalidAccountException extends RuntimeException {}
    public class InvalidAmmountException extends RuntimeException {}

    /**
     * Initiates all account balances with 0 units.
     * @param clientIds account ids
     */
    public CryptocurrencyStorage(String[] clientIds) {
        for (int u = 0; u < clientIds.length; u++) {
            accounts.put(clientIds[u], 0);
            System.out.println(clientIds[u]);
        }
    }

    public boolean isClientIdValid(String clientId) {
        for (Map.Entry<String, Integer> entry : accounts.entrySet()) {
            if (entry.getKey().equals(clientId))
                return true;
        }
        return false;
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
