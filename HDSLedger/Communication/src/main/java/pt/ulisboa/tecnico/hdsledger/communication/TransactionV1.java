package pt.ulisboa.tecnico.hdsledger.communication;

public class TransactionV1 {
    private String sourceId; 
    private String destinationId;
    private int amount;

    public TransactionV1(String source, String destination, int amount) {
        this.sourceId = source;
        this.destinationId = destination;
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
}