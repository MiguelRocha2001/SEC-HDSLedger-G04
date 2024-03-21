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
    public CryptocurrencyStorage(String[] accountIds) {
        for (int u = 0; u < accountIds.length; u++) {
            accounts.put(accountIds[u], 0);
            System.out.println(accountIds[u]);
        }
    }

    public boolean isAccountIdValid(String accountId) {
        for (Map.Entry<String, Integer> entry : accounts.entrySet()) {
            if (entry.getKey().equals(accountId))
                return true;
        }
        return false;
    }

    public int getBalance(String accountId) {
        for (Map.Entry<String, Integer> entry : accounts.entrySet()) {
            if (entry.getKey().equals(accountId))
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
