package pt.ulisboa.tecnico.hdsledger.utilities;

public enum Atack {
    DONT_RESPOND,
    BYZANTINE_UPON_PREPARE_QUORUM, // prepares with a wrong value for round r
    BYZANTINE_UPON_ROUND_CHANGE_QUORUM, // sets a wrong value 
}