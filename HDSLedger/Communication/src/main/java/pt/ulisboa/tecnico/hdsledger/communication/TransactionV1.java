package pt.ulisboa.tecnico.hdsledger.communication;

public class TransactionV1 {
    private String sourceId; 
    private String destinationId;
    private int amount;

    public TransactionV1(String sourceId, String destinationId, int amount) {
        this.sourceId = sourceId;
        this.destinationId = destinationId;
        this.amount = amount;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDestinationId() {
        return destinationId;
    }

    public int getAmount() {
        return amount;
    }

    @Override
    public boolean equals(Object objectArg) {
        if (this == objectArg)
            return true;

        if (!(objectArg instanceof TransactionV1))
            return false;

        TransactionV1 transactionV1 = (TransactionV1) objectArg;
        
        return sourceId.equals(transactionV1.getSourceId()) &&
               destinationId.equals(transactionV1.getDestinationId()) &&
               amount == transactionV1.getAmount();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + sourceId.hashCode();
        result = prime * result + destinationId.hashCode();
        result = prime * result + amount;
        return result;
    }
}