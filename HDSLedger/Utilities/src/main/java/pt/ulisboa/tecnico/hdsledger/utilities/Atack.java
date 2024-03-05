package pt.ulisboa.tecnico.hdsledger.utilities;

public enum Atack {
    DONT_RESPOND,
    BYZANTINE_UPON_PREPARE_QUORUM, // prepares with a wrong value for round r
    BYZANTINE_UPON_ROUND_CHANGE_QUORUM, // sets a wrong value 
    PROPOSE_DIFFERENT_VALUES, // proposes a different value for each process
    FAKE_LEADER // starts proposing values despite not being the leader
}