package pt.ulisboa.tecnico.hdsledger.blockchain.models;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CryptocurrencyStorage {
    private final Map<String, Integer> accounts = new HashMap<>();
    private Lock lock = new ReentrantLock();

    public static class InvalidAccountException extends RuntimeException {}
    public static class InvalidAmmountException extends RuntimeException {}

    /**
     * Initiates all account balances with 10 units.
     * @param clientIds account ids
     */
    public CryptocurrencyStorage(String[] accountIds) {
        for (int u = 0; u < accountIds.length; u++) {
            accounts.put(accountIds[u], 10);
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

    /**
     * Does the transfer in an atomic way.
     */
    public void transfer(String sourceId, String destinationId, int amount) {
        lock.lock();
        int sourceBalance = getBalance(sourceId);
        if (sourceBalance >= amount) {
            accounts.put(sourceId, sourceBalance - amount);
            int destinationBalance = accounts.get(destinationId);
            accounts.put(destinationId, destinationBalance + amount);
            lock.unlock();
        } else {
            lock.unlock();
            throw new InvalidAmmountException();
        }
    }

    /**
     * Does the two transfers in an atomic way.
     */
    public void transferTwoTimes(
        String sourceId, 
        String destinationId1, 
        int amount1, 
        String destinationId2, 
        int amount2,
        boolean checkAmount
    ) {
        lock.lock();
        int sourceBalance = getBalance(sourceId);
        if (checkAmount && sourceBalance < amount1 + amount2) {
            lock.unlock();
            throw new InvalidAmmountException();
        
        } else {
            int newSourceBalance = sourceBalance - amount1;
            accounts.put(sourceId, newSourceBalance);
            int destinationBalance1 = accounts.get(destinationId1);
            accounts.put(destinationId1, destinationBalance1 + amount1);

            accounts.put(sourceId, newSourceBalance - amount2);
            int destinationBalance2 = accounts.get(destinationId2);
            accounts.put(destinationId2, destinationBalance2 + amount2);

            lock.unlock();
        }
    }
}
