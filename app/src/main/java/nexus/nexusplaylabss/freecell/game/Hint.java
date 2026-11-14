package com.nexusplaylabss.freecell.game;

public final class Hint {
    public final Location from;
    public final Location to;
    public final String description;

    public Hint(Location from, Location to, String description) {
        this.from = from;
        this.to = to;
        this.description = description;
    }
}

