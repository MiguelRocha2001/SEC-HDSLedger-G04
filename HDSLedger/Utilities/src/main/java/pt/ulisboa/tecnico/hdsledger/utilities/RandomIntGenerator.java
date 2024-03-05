package pt.ulisboa.tecnico.hdsledger.utilities;

import java.util.Random;

public class RandomIntGenerator {

    public static int generateRandomInt(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
}
