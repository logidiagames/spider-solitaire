package com.nexusplaylabss.freecell.game;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public final class FreeCellGameTest {
    @Test public void dealContainsEveryCardExactlyOnce() {
        FreeCellGame game = new FreeCellGame(20261020L);
        Set<Integer> cards = new HashSet<>();
        for (int column = 0; column < 8; column++) {
            int expected = column < 4 ? 7 : 6;
            assertEquals(expected, game.getColumn(column).size());
            for (Card card : game.getColumn(column)) assertTrue(cards.add(card.id()));
        }
        assertEquals(52, cards.size());
    }

    @Test public void stateRoundTrips() {
        FreeCellGame game = new FreeCellGame(7L);
        FreeCellGame restored = FreeCellGame.restore(game.serialize());
        assertNotNull(restored);
        assertEquals(game.serialize(), restored.serialize());
    }

    @Test public void suggestedMoveCanBePlayedAndUndone() {
        FreeCellGame game = new FreeCellGame(42L);
        String before = game.serialize();
        Hint hint = game.findHint();
        assertNotNull(hint);
        assertTrue(game.move(hint.from, hint.to));
        assertEquals(1, game.getMoves());
        assertTrue(game.undo());
        assertEquals(before, game.serialize());
    }
}

