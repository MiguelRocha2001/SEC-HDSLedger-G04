package pt.ulisboa.tecnico.hdsledger.blockchain.models;

import pt.ulisboa.tecnico.hdsledger.blockchain.services.CriptoService.TransactionV1;

public class TransactionV2 {
    private TransactionV1 transactionv1;
    private String receiverId;

    public TransactionV2(TransactionV1 transactionv1, String receiverId) {
        this.transactionv1 = transactionv1;
        this.receiverId = receiverId;
    }

    public static TransactionV2 createRandom() {
        return new TransactionV2(null, null); // TODO: improve later!
    }
}