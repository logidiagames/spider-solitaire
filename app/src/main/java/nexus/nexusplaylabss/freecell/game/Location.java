package com.nexusplaylabss.freecell.game;

import java.util.Objects;

public final class Location {
    public enum Type { TABLEAU, FREE_CELL, FOUNDATION }

    public final Type type;
    public final int pile;
    public final int cardIndex;

    private Location(Type type, int pile, int cardIndex) {
        this.type = Objects.requireNonNull(type);
        this.pile = pile;
        this.cardIndex = cardIndex;
    }

    public static Location tableau(int column, int cardIndex) {
        return new Location(Type.TABLEAU, column, cardIndex);
    }

    public static Location freeCell(int index) {
        return new Location(Type.FREE_CELL, index, 0);
    }

    public static Location foundation(int suitIndex) {
        return new Location(Type.FOUNDATION, suitIndex, 0);
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Location)) return false;
        Location value = (Location) other;
        return type == value.type && pile == value.pile && cardIndex == value.cardIndex;
    }

    @Override public int hashCode() {
        return Objects.hash(type, pile, cardIndex);
    }
}

