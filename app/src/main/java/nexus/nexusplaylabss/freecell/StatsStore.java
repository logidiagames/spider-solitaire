package com.nexusplaylabss.freecell;

import android.content.SharedPreferences;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

final class StatsStore {
    private static final String GAMES = "stats_games";
    private static final String WINS = "stats_wins";
    private static final String LOSSES = "stats_losses";
    private static final String TOTAL_TIME = "stats_total_time";
    private static final String BEST_TIME = "stats_best_time";
    private static final String TOTAL_MOVES = "stats_total_moves";
    private static final String BEST_MOVES = "stats_best_moves";
    private static final String HISTORY = "stats_history";
    private static final int MAX_HISTORY = 30;

    private final SharedPreferences preferences;

    StatsStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    void recordFinished(boolean won, long seed, String mode, int moves, long elapsedMillis) {
        int games = preferences.getInt(GAMES, 0) + 1;
        int wins = preferences.getInt(WINS, 0) + (won ? 1 : 0);
        int losses = preferences.getInt(LOSSES, 0) + (won ? 0 : 1);
        SharedPreferences.Editor editor = preferences.edit()
                .putInt(GAMES, games)
                .putInt(WINS, wins)
                .putInt(LOSSES, losses);
        if (won) {
            long seconds = Math.max(1, elapsedMillis / 1000L);
            long bestTime = preferences.getLong(BEST_TIME, 0L);
            int bestMoves = preferences.getInt(BEST_MOVES, 0);
            editor.putLong(TOTAL_TIME, preferences.getLong(TOTAL_TIME, 0L) + seconds)
                    .putInt(TOTAL_MOVES, preferences.getInt(TOTAL_MOVES, 0) + moves)
                    .putLong(BEST_TIME, bestTime == 0L ? seconds : Math.min(bestTime, seconds))
                    .putInt(BEST_MOVES, bestMoves == 0 ? moves : Math.min(bestMoves, moves));
        }
        editor.putString(HISTORY, appendHistory(won, seed, mode, moves, elapsedMillis)).apply();
    }

    String summary() {
        int games = preferences.getInt(GAMES, 0);
        int wins = preferences.getInt(WINS, 0);
        long totalTime = preferences.getLong(TOTAL_TIME, 0L);
        int totalMoves = preferences.getInt(TOTAL_MOVES, 0);
        String winRate = games == 0 ? "0%" : Math.round(wins * 100f / games) + "%";
        return "Jogos: " + games
                + "\nVitórias: " + wins
                + "\nDerrotas/abandonados: " + preferences.getInt(LOSSES, 0)
                + "\nPercentagem de vitória: " + winRate
                + "\nMelhor tempo: " + formatSeconds(preferences.getLong(BEST_TIME, 0L))
                + "\nMenos movimentos: " + preferences.getInt(BEST_MOVES, 0)
                + "\nTempo médio de vitória: " + formatSeconds(wins == 0 ? 0 : totalTime / wins)
                + "\nMédia de movimentos: " + (wins == 0 ? 0 : totalMoves / wins);
    }

    String[] historyLabels() {
        List<Entry> entries = entries();
        if (entries.isEmpty()) return new String[]{"Ainda não existem jogos no histórico."};
        String[] labels = new String[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(entry.timestamp));
            labels[i] = (entry.won ? "✓ " : "○ ") + entry.mode + "  •  " + date
                    + "\n" + entry.moves + " movimentos  •  " + formatSeconds(entry.seconds)
                    + "  •  #" + Math.abs(entry.seed);
        }
        return labels;
    }

    Long seedAt(int index) {
        List<Entry> entries = entries();
        return index >= 0 && index < entries.size() ? entries.get(index).seed : null;
    }

    void reset() {
        preferences.edit()
                .remove(GAMES).remove(WINS).remove(LOSSES).remove(TOTAL_TIME)
                .remove(BEST_TIME).remove(TOTAL_MOVES).remove(BEST_MOVES).remove(HISTORY)
                .apply();
    }

    private String appendHistory(boolean won, long seed, String mode, int moves, long elapsedMillis) {
        String safeMode = mode.replace("~", " ").replace("|", " ");
        String entry = System.currentTimeMillis() + "~" + (won ? 1 : 0) + "~" + seed + "~"
                + moves + "~" + Math.max(0, elapsedMillis / 1000L) + "~" + safeMode;
        String previous = preferences.getString(HISTORY, "");
        String combined = previous.isEmpty() ? entry : entry + "||" + previous;
        String[] values = combined.split("\\|\\|", -1);
        StringBuilder limited = new StringBuilder();
        for (int i = 0; i < Math.min(MAX_HISTORY, values.length); i++) {
            if (i > 0) limited.append("||");
            limited.append(values[i]);
        }
        return limited.toString();
    }

    private List<Entry> entries() {
        ArrayList<Entry> result = new ArrayList<>();
        String encoded = preferences.getString(HISTORY, "");
        if (encoded.isEmpty()) return result;
        for (String row : encoded.split("\\|\\|", -1)) {
            String[] values = row.split("~", 6);
            if (values.length != 6) continue;
            try {
                result.add(new Entry(Long.parseLong(values[0]), "1".equals(values[1]),
                        Long.parseLong(values[2]), Integer.parseInt(values[3]),
                        Long.parseLong(values[4]), values[5]));
            } catch (NumberFormatException ignored) {
                // Skip malformed legacy entries.
            }
        }
        return result;
    }

    private static String formatSeconds(long seconds) {
        if (seconds <= 0) return "--:--";
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private static final class Entry {
        final long timestamp;
        final boolean won;
        final long seed;
        final int moves;
        final long seconds;
        final String mode;

        Entry(long timestamp, boolean won, long seed, int moves, long seconds, String mode) {
            this.timestamp = timestamp;
            this.won = won;
            this.seed = seed;
            this.moves = moves;
            this.seconds = seconds;
            this.mode = mode;
        }
    }
}
