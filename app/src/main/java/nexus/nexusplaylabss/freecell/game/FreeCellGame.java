package com.nexusplaylabss.freecell.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class FreeCellGame {
    private static final int COLUMN_COUNT = 8;
    private static final int CELL_COUNT = 4;
    private static final int MAX_UNDO = 128;

    private final List<ArrayList<Card>> tableau = new ArrayList<>(COLUMN_COUNT);
    private final Card[] freeCells = new Card[CELL_COUNT];
    private final int[] foundations = new int[CELL_COUNT];
    private final Deque<Snapshot> history = new ArrayDeque<>();
    private long seed;
    private int moves;

    public FreeCellGame(long seed) {
        deal(seed);
    }

    public void deal(long newSeed) {
        seed = newSeed;
        moves = 0;
        history.clear();
        tableau.clear();
        for (int i = 0; i < COLUMN_COUNT; i++) tableau.add(new ArrayList<>());
        for (int i = 0; i < CELL_COUNT; i++) {
            freeCells[i] = null;
            foundations[i] = 0;
        }

        ArrayList<Card> deck = new ArrayList<>(52);
        for (Card.Suit suit : Card.Suit.values()) {
            for (int rank = 1; rank <= 13; rank++) deck.add(new Card(suit, rank));
        }
        Collections.shuffle(deck, new Random(seed));
        for (int i = 0; i < deck.size(); i++) tableau.get(i % COLUMN_COUNT).add(deck.get(i));
    }

    public long getSeed() { return seed; }
    public int getMoves() { return moves; }
    public boolean canUndo() { return !history.isEmpty(); }

    public List<Card> getColumn(int index) {
        return Collections.unmodifiableList(tableau.get(index));
    }

    public Card getFreeCell(int index) { return freeCells[index]; }
    public int getFoundationRank(int suitIndex) { return foundations[suitIndex]; }

    public Card cardAt(Location location) {
        if (!validPile(location)) return null;
        switch (location.type) {
            case TABLEAU:
                List<Card> column = tableau.get(location.pile);
                return location.cardIndex >= 0 && location.cardIndex < column.size()
                        ? column.get(location.cardIndex) : null;
            case FREE_CELL:
                return freeCells[location.pile];
            case FOUNDATION:
                int rank = foundations[location.pile];
                return rank == 0 ? null : new Card(Card.Suit.values()[location.pile], rank);
            default:
                return null;
        }
    }

    public boolean move(Location from, Location to) {
        if (from == null || to == null || from.equals(to) || !validPile(from) || !validPile(to)) {
            return false;
        }
        Snapshot before = snapshot();
        boolean moved;
        if (to.type == Location.Type.TABLEAU) {
            moved = moveToTableau(from, to.pile);
        } else if (to.type == Location.Type.FREE_CELL) {
            moved = moveToFreeCell(from, to.pile);
        } else {
            moved = moveToFoundation(from, to.pile);
        }
        if (moved) {
            history.push(before);
            while (history.size() > MAX_UNDO) history.removeLast();
            moves++;
        }
        return moved;
    }

    public boolean undo() {
        if (history.isEmpty()) return false;
        restoreSnapshot(history.pop());
        return true;
    }

    public boolean isWon() {
        for (int rank : foundations) if (rank != 13) return false;
        return true;
    }

    public Hint findHint() {
        Hint foundation = findFoundationMove();
        if (foundation != null) return foundation;
        for (int from = 0; from < COLUMN_COUNT; from++) {
            List<Card> source = tableau.get(from);
            for (int start = 0; start < source.size(); start++) {
                if (!isOrderedRun(source, start)) continue;
                for (int to = 0; to < COLUMN_COUNT; to++) {
                    if (from != to && canPlaceRunOnTableau(source, start, to)) {
                        return new Hint(Location.tableau(from, start), Location.tableau(to, 0),
                                "Move " + source.get(start).label() + " and its run to column " + (to + 1));
                    }
                }
            }
        }
        int emptyCell = firstEmptyFreeCell();
        if (emptyCell >= 0) {
            for (int col = 0; col < COLUMN_COUNT; col++) {
                List<Card> cards = tableau.get(col);
                if (!cards.isEmpty()) {
                    int bottom = cards.size() - 1;
                    return new Hint(Location.tableau(col, bottom), Location.freeCell(emptyCell),
                            "Temporarily move " + cards.get(bottom).label() + " to a free cell");
                }
            }
        }
        return null;
    }

    public Hint findFoundationMove() {
        for (int i = 0; i < CELL_COUNT; i++) {
            Card card = freeCells[i];
            if (card != null && canPlaceOnFoundation(card, card.suit.ordinal())) {
                return new Hint(Location.freeCell(i), Location.foundation(card.suit.ordinal()),
                        "Move " + card.label() + " to its foundation");
            }
        }
        for (int col = 0; col < COLUMN_COUNT; col++) {
            List<Card> cards = tableau.get(col);
            if (!cards.isEmpty()) {
                int bottom = cards.size() - 1;
                Card card = cards.get(bottom);
                if (canPlaceOnFoundation(card, card.suit.ordinal())) {
                    return new Hint(Location.tableau(col, bottom), Location.foundation(card.suit.ordinal()),
                            "Move " + card.label() + " to its foundation");
                }
            }
        }
        return null;
    }

    public int foundationCardCount() {
        int total = 0;
        for (int rank : foundations) total += rank;
        return total;
    }

    private boolean moveToTableau(Location from, int destination) {
        ArrayList<Card> dest = tableau.get(destination);
        if (from.type == Location.Type.TABLEAU) {
            if (from.pile == destination) return false;
            ArrayList<Card> source = tableau.get(from.pile);
            if (from.cardIndex < 0 || from.cardIndex >= source.size()
                    || !canPlaceRunOnTableau(source, from.cardIndex, destination)) return false;
            List<Card> run = new ArrayList<>(source.subList(from.cardIndex, source.size()));
            source.subList(from.cardIndex, source.size()).clear();
            dest.addAll(run);
            return true;
        }

        if (from.type == Location.Type.FOUNDATION) return false;

        Card card = cardAt(from);
        if (card == null || !canStack(card, dest)) return false;
        removeTop(from);
        dest.add(card);
        return true;
    }

    private boolean moveToFreeCell(Location from, int destination) {
        if (freeCells[destination] != null || from.type != Location.Type.TABLEAU) return false;
        ArrayList<Card> source = tableau.get(from.pile);
        if (source.isEmpty() || from.cardIndex != source.size() - 1) return false;
        freeCells[destination] = source.remove(source.size() - 1);
        return true;
    }

    private boolean moveToFoundation(Location from, int destination) {
        if (from.type == Location.Type.FOUNDATION) return false;
        Card card = cardAt(from);
        if (card == null || !canPlaceOnFoundation(card, destination)) return false;
        if (from.type == Location.Type.TABLEAU) {
            ArrayList<Card> source = tableau.get(from.pile);
            if (from.cardIndex != source.size() - 1) return false;
        }
        removeTop(from);
        foundations[destination]++;
        return true;
    }

    private void removeTop(Location location) {
        if (location.type == Location.Type.TABLEAU) {
            ArrayList<Card> source = tableau.get(location.pile);
            source.remove(source.size() - 1);
        } else if (location.type == Location.Type.FREE_CELL) {
            freeCells[location.pile] = null;
        } else {
            foundations[location.pile]--;
        }
    }

    private boolean canPlaceRunOnTableau(List<Card> source, int start, int destination) {
        if (!isOrderedRun(source, start)) return false;
        int runSize = source.size() - start;
        if (runSize > movableCapacity(destination)) return false;
        return canStack(source.get(start), tableau.get(destination));
    }

    private boolean isOrderedRun(List<Card> source, int start) {
        if (start < 0 || start >= source.size()) return false;
        for (int i = start; i < source.size() - 1; i++) {
            Card upper = source.get(i);
            Card lower = source.get(i + 1);
            if (upper.rank != lower.rank + 1 || upper.suit.red == lower.suit.red) return false;
        }
        return true;
    }

    private boolean canStack(Card moving, List<Card> destination) {
        if (destination.isEmpty()) return true;
        Card top = destination.get(destination.size() - 1);
        return top.rank == moving.rank + 1 && top.suit.red != moving.suit.red;
    }

    private int movableCapacity(int destination) {
        int emptyCells = 0;
        for (Card card : freeCells) if (card == null) emptyCells++;
        int emptyColumns = 0;
        for (int i = 0; i < COLUMN_COUNT; i++) {
            if (i != destination && tableau.get(i).isEmpty()) emptyColumns++;
        }
        return (emptyCells + 1) * (1 << emptyColumns);
    }

    private boolean canPlaceOnFoundation(Card card, int destination) {
        return card.suit.ordinal() == destination && foundations[destination] + 1 == card.rank;
    }

    private int firstEmptyFreeCell() {
        for (int i = 0; i < CELL_COUNT; i++) if (freeCells[i] == null) return i;
        return -1;
    }

    private boolean validPile(Location location) {
        return location.pile >= 0 && location.pile < (location.type == Location.Type.TABLEAU ? 8 : 4);
    }

    public String serialize() {
        StringBuilder out = new StringBuilder("1|").append(seed).append('|').append(moves).append('|');
        appendInts(out, foundations).append('|');
        for (int i = 0; i < freeCells.length; i++) {
            if (i > 0) out.append(',');
            out.append(freeCells[i] == null ? -1 : freeCells[i].id());
        }
        out.append('|');
        for (int col = 0; col < tableau.size(); col++) {
            if (col > 0) out.append(';');
            for (int i = 0; i < tableau.get(col).size(); i++) {
                if (i > 0) out.append(',');
                out.append(tableau.get(col).get(i).id());
            }
        }
        return out.toString();
    }

    public static FreeCellGame restore(String encoded) {
        if (encoded == null) return null;
        try {
            String[] parts = encoded.split("\\|", -1);
            if (parts.length != 6 || !"1".equals(parts[0])) return null;
            FreeCellGame game = new FreeCellGame(Long.parseLong(parts[1]));
            game.moves = Integer.parseInt(parts[2]);
            parseInts(parts[3], game.foundations, false);
            int[] cells = new int[4];
            parseInts(parts[4], cells, true);
            for (int i = 0; i < cells.length; i++) game.freeCells[i] = cells[i] < 0 ? null : Card.fromId(cells[i]);
            String[] columns = parts[5].split(";", -1);
            if (columns.length != 8) return null;
            game.tableau.clear();
            for (String value : columns) {
                ArrayList<Card> column = new ArrayList<>();
                if (!value.isEmpty()) for (String id : value.split(",")) column.add(Card.fromId(Integer.parseInt(id)));
                game.tableau.add(column);
            }
            game.history.clear();
            return game.isValidState() ? game : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isValidState() {
        Set<Integer> visible = new HashSet<>();
        int count = 0;
        for (int suit = 0; suit < 4; suit++) {
            if (foundations[suit] < 0 || foundations[suit] > 13) return false;
            count += foundations[suit];
            for (int rank = 1; rank <= foundations[suit]; rank++) visible.add(suit * 13 + rank - 1);
        }
        for (Card card : freeCells) if (card != null) { count++; if (!visible.add(card.id())) return false; }
        for (List<Card> column : tableau) for (Card card : column) { count++; if (!visible.add(card.id())) return false; }
        return count == 52 && visible.size() == 52;
    }

    private static StringBuilder appendInts(StringBuilder out, int[] values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.append(',');
            out.append(values[i]);
        }
        return out;
    }

    private static void parseInts(String text, int[] output, boolean allowNegative) {
        String[] values = text.split(",", -1);
        if (values.length != output.length) throw new IllegalArgumentException("Wrong value count");
        for (int i = 0; i < values.length; i++) {
            output[i] = Integer.parseInt(values[i]);
            if (!allowNegative && output[i] < 0) throw new IllegalArgumentException("Negative value");
        }
    }

    private Snapshot snapshot() {
        ArrayList<ArrayList<Card>> columns = new ArrayList<>(COLUMN_COUNT);
        for (ArrayList<Card> column : tableau) columns.add(new ArrayList<>(column));
        return new Snapshot(columns, freeCells.clone(), foundations.clone(), moves);
    }

    private void restoreSnapshot(Snapshot value) {
        tableau.clear();
        for (ArrayList<Card> column : value.tableau) tableau.add(new ArrayList<>(column));
        System.arraycopy(value.freeCells, 0, freeCells, 0, freeCells.length);
        System.arraycopy(value.foundations, 0, foundations, 0, foundations.length);
        moves = value.moves;
    }

    private static final class Snapshot {
        final ArrayList<ArrayList<Card>> tableau;
        final Card[] freeCells;
        final int[] foundations;
        final int moves;

        Snapshot(ArrayList<ArrayList<Card>> tableau, Card[] freeCells, int[] foundations, int moves) {
            this.tableau = tableau;
            this.freeCells = freeCells;
            this.foundations = foundations;
            this.moves = moves;
        }
    }
}

