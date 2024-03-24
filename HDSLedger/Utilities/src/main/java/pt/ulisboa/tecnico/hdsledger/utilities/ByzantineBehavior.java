package pt.ulisboa.tecnico.hdsledger.utilities;

public enum ByzantineBehavior {
    NONE,
    IGNORE_REQUESTS,
    FAKE_LEADER, // starts proposing values despite not being the leader,
    BAD_LEADER_PROPOSE_WITH_GENERATED_SIGNATURE, // proposes a random value, and signs it with self Private Key.
    BAD_LEADER_PROPOSE_WITH_ORIGINAL_SIGNATURE, // proposes a random value, and uses the signature of the original to be proposed value
    BYZANTINE_UPON_PREPARE_QUORUM, // prepares with a wrong value for round r
    BYZANTINE_UPON_ROUND_CHANGE_QUORUM, // Changes the input value to a newly generated random value
    DONT_VERIFY_SIGNATURES,
    FAKE_LEADER_WITH_FORGED_PRE_PREPARE_MESSAGE, // broadcast PRE-PREPARE message with senderId of real leader
    DONT_VALIDATE_TRANSACTION,
    IS_BYZANTINE, // any client byzantine behavior
    IS_BYZANTINE_AND_NOT_REGISTERED // Any client that is not registered and should be ignored in the loading by correct nodes,
}