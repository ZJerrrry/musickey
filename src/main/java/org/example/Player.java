package org.example;

public class Player {
    private int score = 0;

    public void addScore(int points) {
        score += points;
    }

    public int getScore() {
        return score;
    }
}
