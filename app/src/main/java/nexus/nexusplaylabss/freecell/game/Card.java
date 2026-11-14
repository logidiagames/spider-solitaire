package com.nexusplaylabss.freecell.game;

import java.util.Objects;

public final class Card {
    public enum Suit {
        CLUBS("♣", false), DIAMONDS("♦", true), HEARTS("♥", true), SPADES("♠", false);

        public final String symbol;
        public final boolean red;

        Suit(String symbol, boolean red) {
            this.symbol = symbol;
            this.red = red;
        }
    }

    public final Suit suit;
    public final int rank;

    public Card(Suit suit, int rank) {
        if (rank < 1 || rank > 13) throw new IllegalArgumentException("Rank must be 1..13");
        this.suit = Objects.requireNonNull(suit);
        this.rank = rank;
    }

    public int id() {
        return suit.ordinal() * 13 + rank - 1;
    }

    public static Card fromId(int id) {
        if (id < 0 || id >= 52) throw new IllegalArgumentException("Card id must be 0..51");
        return new Card(Suit.values()[id / 13], id % 13 + 1);
    }

    public String rankLabel() {
        switch (rank) {
            case 1: return "A";
            case 11: return "J";
            case 12: return "Q";
            case 13: return "K";
            default: return Integer.toString(rank);
        }
    }

    public String label() {
        return rankLabel() + suit.symbol;
    }

    @Override public boolean equals(Object other) {
        return other instanceof Card && ((Card) other).id() == id();
    }

    @Override public int hashCode() {
        return id();
    }

    @Override public String toString() {
        return label();
    }
}

