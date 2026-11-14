package com.nexusplaylabss.freecell.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.nexusplaylabss.freecell.R;
import com.nexusplaylabss.freecell.game.Card;
import com.nexusplaylabss.freecell.game.FreeCellGame;
import com.nexusplaylabss.freecell.game.Hint;
import com.nexusplaylabss.freecell.game.Location;

@SuppressLint("ViewConstructor")
public final class FreeCellView extends View {
    public interface Listener {
        void onStateChanged();
        void onSettingsRequested();
        void onThemeRequested();
        void onNewGameRequested();
        void onGameWon();
    }

    private static final int GOLD = Color.rgb(248, 205, 91);
    private static final int CREAM = Color.rgb(255, 253, 247);
    private static final int RED = Color.rgb(190, 32, 48);
    private static final int BLACK = Color.rgb(27, 31, 34);
    private static final String[] CONTROL_ICONS = {"⚙", "▣", "+", "♧", "↩"};
    private static final String[] CONTROL_LABELS = {"Definições", "Tema", "Novo Jogo", "Dica", "Desfazer"};

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF[] topSlots = new RectF[8];
    private final RectF[] controls = new RectF[5];
    private final Listener listener;
    private final float density;
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 35);

    private FreeCellGame game;
    private String gameMode;
    private Location selected;
    private Location pointerSource;
    private boolean dragging;
    private float downX;
    private float downY;
    private float dragX;
    private float dragY;
    private long lastInteraction;
    private long elapsedBeforeStart;
    private long clockStartedAt;
    private boolean clockRunning = true;
    private boolean winReported;
    private Location invalidLocation;
    private long invalidStarted;

    private boolean sound = true;
    private boolean vibration = true;
    private boolean tapMove = true;
    private boolean autoComplete = true;
    private boolean autoHint;
    private boolean highlight = true;
    private boolean celebration = true;
    private boolean leftHanded;
    private boolean freeCellsRight;
    private boolean quickGame;
    private int cardStyle;
    private int tableStyle;

    private float cardWidth;
    private float cardHeight;
    private float boardLeft;
    private float gap;
    private float tableauTop;
    private float tableauBottom;
    private float overlap;
    private LinearGradient background;
    private long celebrationStarted;

    private final Runnable autoHintTask = new Runnable() {
        @Override public void run() {
            if (autoHint && !dragging && selected == null && !game.isWon()
                    && SystemClock.elapsedRealtime() - lastInteraction >= 15000L) {
                showHint(false);
            }
            postDelayed(this, 5000L);
        }
    };

    public FreeCellView(Context context, FreeCellGame game, long elapsedMillis,
                        String gameMode, Listener listener) {
        super(context);
        this.game = game;
        this.gameMode = gameMode == null ? "Jogo Aleatório" : gameMode;
        this.listener = listener;
        density = getResources().getDisplayMetrics().density;
        elapsedBeforeStart = Math.max(0L, elapsedMillis);
        clockStartedAt = SystemClock.elapsedRealtime();
        lastInteraction = SystemClock.elapsedRealtime();
        textPaint.setTypeface(Typeface.create("sans", Typeface.BOLD));
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setWillNotDraw(false);
        setBackgroundColor(Color.rgb(7, 105, 70));
        setFocusable(true);
        setClickable(true);
        setContentDescription("Mesa de FreeCell");
        postDelayed(autoHintTask, 5000L);
    }

    public FreeCellGame getGame() { return game; }
    public String getGameMode() { return gameMode; }

    public void configure(boolean sound, boolean vibration, boolean tapMove,
                          boolean autoComplete, boolean autoHint, boolean highlight,
                          boolean celebration, boolean leftHanded, boolean freeCellsRight,
                          boolean quickGame, int cardStyle, int tableStyle) {
        this.sound = sound;
        this.vibration = vibration;
        this.tapMove = tapMove;
        this.autoComplete = autoComplete;
        this.autoHint = autoHint;
        this.highlight = highlight;
        this.celebration = celebration;
        this.leftHanded = leftHanded;
        this.freeCellsRight = freeCellsRight;
        this.quickGame = quickGame;
        this.cardStyle = Math.max(0, Math.min(2, cardStyle));
        this.tableStyle = Math.max(0, Math.min(2, tableStyle));
        updateBackground();
        invalidate();
    }

    public long getElapsedMillis() {
        return clockRunning
                ? elapsedBeforeStart + SystemClock.elapsedRealtime() - clockStartedAt
                : elapsedBeforeStart;
    }

    public void pauseClock() {
        if (!clockRunning) return;
        elapsedBeforeStart = getElapsedMillis();
        clockRunning = false;
    }

    public void resumeClock() {
        if (clockRunning) return;
        clockStartedAt = SystemClock.elapsedRealtime();
        clockRunning = true;
        invalidate();
    }

    public void startNewGame(long seed, String mode) {
        game = new FreeCellGame(seed);
        gameMode = mode;
        selected = null;
        pointerSource = null;
        dragging = false;
        invalidLocation = null;
        invalidStarted = 0L;
        winReported = false;
        celebrationStarted = 0L;
        elapsedBeforeStart = 0L;
        clockStartedAt = SystemClock.elapsedRealtime();
        clockRunning = true;
        lastInteraction = SystemClock.elapsedRealtime();
        requestLayout();
        invalidate();
        announceForAccessibility("Novo jogo: " + mode);
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(autoHintTask);
        tone.release();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        updateBackground();
        layoutBoard(width, height);
    }

    private void updateBackground() {
        int top;
        int bottom;
        if (tableStyle == 1) {
            top = Color.rgb(20, 120, 74);
            bottom = Color.rgb(4, 67, 43);
        } else if (tableStyle == 2) {
            top = Color.rgb(17, 112, 94);
            bottom = Color.rgb(4, 58, 54);
        } else {
            top = Color.rgb(11, 119, 76);
            bottom = Color.rgb(4, 73, 50);
        }
        background = new LinearGradient(0, 0, 0, Math.max(1, getHeight()),
                top, bottom, Shader.TileMode.CLAMP);
    }

    private void layoutBoard(int width, int height) {
        float usableWidth = width - getPaddingLeft() - getPaddingRight();
        gap = Math.max(dp(2), usableWidth * 0.008f);
        boardLeft = getPaddingLeft() + gap;
        cardWidth = (usableWidth - gap * 9f) / 8f;
        cardHeight = cardWidth * 1.52f;
        float headerHeight = dp(54);
        float top = getPaddingTop() + headerHeight;
        for (int i = 0; i < 8; i++) {
            float left = boardLeft + i * (cardWidth + gap);
            topSlots[i] = new RectF(left, top, left + cardWidth, top + cardHeight);
        }
        float controlsHeight = dp(90);
        tableauTop = top + cardHeight + dp(10);
        tableauBottom = height - getPaddingBottom() - controlsHeight - dp(7);
        recalculateOverlap();

        float controlsTop = height - getPaddingBottom() - controlsHeight + dp(3);
        float buttonGap = dp(3);
        float buttonWidth = (usableWidth - dp(14) - buttonGap * 4) / 5f;
        float left = getPaddingLeft() + dp(7);
        for (int i = 0; i < controls.length; i++) {
            controls[i] = new RectF(left + i * (buttonWidth + buttonGap), controlsTop,
                    left + i * (buttonWidth + buttonGap) + buttonWidth, controlsTop + dp(78));
        }
    }

    private void recalculateOverlap() {
        int largest = 1;
        for (int i = 0; i < 8; i++) largest = Math.max(largest, game.getColumn(i).size());
        float room = Math.max(dp(20), tableauBottom - tableauTop - cardHeight);
        overlap = Math.min(cardHeight * 0.33f, room / Math.max(1, largest - 1));
        overlap = Math.max(dp(13), overlap);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(background);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);
        paint.setShader(null);
        drawTableTexture(canvas);
        drawHeader(canvas);
        drawTopArea(canvas);
        drawTableau(canvas);
        drawControls(canvas);
        if (dragging && pointerSource != null) drawDraggedCards(canvas);
        drawCelebration(canvas);
        postInvalidateDelayed(1000L);
    }

    private void drawTableTexture(Canvas canvas) {
        paint.setColor(Color.argb(tableStyle == 1 ? 15 : 9, 255, 255, 255));
        paint.setStrokeWidth(dp(1));
        for (float y = getPaddingTop() + dp(60); y < getHeight(); y += dp(22)) {
            canvas.drawLine(0, y, getWidth(), y + dp(7), paint);
        }
    }

    private void drawHeader(Canvas canvas) {
        paint.setColor(Color.argb(90, 0, 0, 0));
        canvas.drawRect(0, getPaddingTop(), getWidth(), getPaddingTop() + dp(48), paint);
        String[] values = {
                "Score " + Math.max(0, game.foundationCardCount() * 10 - game.getMoves()),
                "Time " + formatTime(getElapsedMillis()),
                "Moves " + game.getMoves()
        };
        float third = getWidth() / 3f;
        for (int i = 0; i < 3; i++) {
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(dp(17));
            canvas.drawText(values[i], third * (i + .5f), getPaddingTop() + dp(31), textPaint);
        }
    }

    private void drawTopArea(Canvas canvas) {
        for (int visual = 0; visual < 8; visual++) {
            int logical = slotLogicalIndex(visual);
            RectF rect = topSlots[visual];
            if (logical < 4) {
                drawSlot(canvas, rect, "+");
                Card card = game.getFreeCell(logical);
                if (card != null && !isDragged(Location.freeCell(logical))) {
                    Location location = Location.freeCell(logical);
                    drawCard(canvas, card, shakenRect(location, rect), location.equals(selected));
                }
            } else {
                int suit = logical - 4;
                drawSlot(canvas, rect, Card.Suit.values()[suit].symbol);
                int rank = game.getFoundationRank(suit);
                if (rank > 0) drawCard(canvas, new Card(Card.Suit.values()[suit], rank), rect, false);
            }
        }
    }

    private int slotLogicalIndex(int visual) {
        if (!freeCellsRight) return visual;
        return visual < 4 ? visual + 4 : visual - 4;
    }

    private void drawSlot(Canvas canvas, RectF rect, String label) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(65, 0, 0, 0));
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(Color.argb(100, 255, 255, 255));
        canvas.drawRoundRect(rect, dp(5), dp(5), paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(cardWidth * .42f);
        textPaint.setColor(Color.argb(115, 255, 255, 255));
        canvas.drawText(label, rect.centerX(), rect.centerY() + cardWidth * .15f, textPaint);
    }

    private void drawTableau(Canvas canvas) {
        for (int visualCol = 0; visualCol < 8; visualCol++) {
            int col = leftHanded ? 7 - visualCol : visualCol;
            List<Card> cards = game.getColumn(col);
            float left = boardLeft + visualCol * (cardWidth + gap);
            if (cards.isEmpty()) drawSlot(canvas,
                    new RectF(left, tableauTop, left + cardWidth, tableauTop + cardHeight), "");
            for (int index = 0; index < cards.size(); index++) {
                if (dragging && pointerSource != null && pointerSource.type == Location.Type.TABLEAU
                        && pointerSource.pile == col && index >= pointerSource.cardIndex) continue;
                float top = tableauTop + index * overlap;
                RectF rect = new RectF(left, top, left + cardWidth, top + cardHeight);
                boolean chosen = selected != null && selected.type == Location.Type.TABLEAU
                        && selected.pile == col && index >= selected.cardIndex;
                Location location = Location.tableau(col, index);
                drawCard(canvas, cards.get(index), shakenRect(location, rect), chosen,
                        index == cards.size() - 1);
            }
        }
    }

    private RectF shakenRect(Location location, RectF original) {
        if (invalidLocation == null || !invalidLocation.equals(location)) return original;
        long age = SystemClock.elapsedRealtime() - invalidStarted;
        if (age >= 360L) {
            invalidLocation = null;
            invalidStarted = 0L;
            return original;
        }
        float progress = age / 360f;
        float offset = (float) Math.sin(age / 18f) * dp(6) * (1f - progress);
        RectF moved = new RectF(original);
        moved.offset(offset, 0f);
        postInvalidateDelayed(16L);
        return moved;
    }

    private void drawCard(Canvas canvas, Card card, RectF rect, boolean chosen) {
        drawCard(canvas, card, rect, chosen, true);
    }

    private void drawCard(Canvas canvas, Card card, RectF rect, boolean chosen, boolean fullFace) {
        float radius = cardStyle == 2 ? dp(2) : dp(6);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(dp(2), dp(1), dp(2), Color.argb(100, 0, 0, 0));
        paint.setColor(cardStyle == 1 ? Color.WHITE : CREAM);
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.clearShadowLayer();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(chosen && highlight ? dp(3) : dp(1));
        paint.setColor(chosen && highlight ? GOLD : Color.rgb(205, 202, 194));
        canvas.drawRoundRect(rect, radius, radius, paint);

        int color = card.suit.red ? RED : BLACK;
        textPaint.setColor(color);
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(cardWidth * (cardStyle == 1 ? .43f : .39f));
        float cornerBaseline = rect.top + cardWidth * .38f;
        canvas.drawText(card.rankLabel(), rect.left + cardWidth * .07f, cornerBaseline, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(card.suit.symbol, rect.right - cardWidth * .07f, cornerBaseline, textPaint);

        if (fullFace && cardStyle != 2 && rect.height() > cardWidth) {
            if (card.rank >= 11) {
                drawCourtFigure(canvas, card, rect, color);
            } else {
                textPaint.setTextAlign(Paint.Align.CENTER);
                textPaint.setTextSize(cardWidth * (cardStyle == 1 ? .72f : .64f));
                canvas.drawText(card.suit.symbol, rect.centerX(),
                        rect.top + cardHeight * .73f, textPaint);
            }
        }
    }

    private void drawCourtFigure(Canvas canvas, Card card, RectF cardRect, int color) {
        float width = cardWidth * .62f;
        float left = cardRect.centerX() - width / 2f;
        float top = cardRect.top + cardWidth * .48f;
        RectF frame = new RectF(left, top, left + width,
                Math.min(cardRect.bottom - cardWidth * .07f, top + cardWidth * .76f));

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(242, 210, 112));
        canvas.drawRoundRect(frame, dp(2), dp(2), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(color);
        canvas.drawRoundRect(frame, dp(2), dp(2), paint);

        float headX = frame.centerX();
        float headY = frame.top + frame.height() * .38f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(235, 184, 128));
        canvas.drawCircle(headX, headY, frame.width() * .16f, paint);

        Path body = new Path();
        body.moveTo(frame.left + frame.width() * .12f, frame.bottom);
        body.lineTo(headX - frame.width() * .16f, headY + frame.height() * .10f);
        body.lineTo(headX + frame.width() * .16f, headY + frame.height() * .10f);
        body.lineTo(frame.right - frame.width() * .12f, frame.bottom);
        body.close();
        paint.setColor(color);
        canvas.drawPath(body, paint);

        Path crown = new Path();
        float crownY = headY - frame.height() * .20f;
        crown.moveTo(headX - frame.width() * .20f, crownY + frame.height() * .12f);
        crown.lineTo(headX - frame.width() * .15f, crownY);
        crown.lineTo(headX, crownY + frame.height() * .09f);
        crown.lineTo(headX + frame.width() * .15f, crownY);
        crown.lineTo(headX + frame.width() * .20f, crownY + frame.height() * .12f);
        crown.close();
        paint.setColor(Color.rgb(132, 98, 16));
        canvas.drawPath(crown, paint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(frame.width() * .34f);
        textPaint.setColor(color);
        canvas.drawText(card.suit.symbol, frame.right - dp(1), frame.bottom - dp(1), textPaint);
    }

    private void drawControls(Canvas canvas) {
        float barTop = controls[0].top - dp(4);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(85, 0, 45, 24));
        canvas.drawRect(0, barTop, getWidth(), getHeight(), paint);

        String modeLabel = gameMode.length() > 20 ? gameMode.substring(0, 20) : gameMode;
        RectF modeRect = new RectF(0, barTop - dp(34), dp(126), barTop);
        paint.setColor(Color.argb(105, 0, 45, 24));
        canvas.drawRoundRect(modeRect, 0, dp(7), paint);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(13));
        canvas.drawText(modeLabel, modeRect.centerX(), modeRect.centerY() + dp(5), textPaint);

        for (int visual = 0; visual < controls.length; visual++) {
            int action = leftHanded ? controls.length - 1 - visual : visual;
            RectF rect = controls[visual];
            boolean disabled = action == 4 && !game.canUndo();
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(disabled ? Color.argb(85, 255, 255, 255) : Color.WHITE);
            float iconY = rect.top + dp(25);
            canvas.drawCircle(rect.centerX(), iconY, dp(21), paint);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setColor(disabled ? Color.argb(100, 255, 255, 255) : Color.WHITE);
            textPaint.setTextSize(dp(23));
            canvas.drawText(CONTROL_ICONS[action], rect.centerX(), iconY + dp(8), textPaint);
            textPaint.setTextSize(dp(10));
            canvas.drawText(CONTROL_LABELS[action], rect.centerX(), rect.bottom - dp(7), textPaint);
        }
    }

    private void drawDraggedCards(Canvas canvas) {
        List<Card> cards = draggedCards();
        float left = dragX - cardWidth / 2f;
        float firstTop = dragY - dp(22);
        for (int i = 0; i < cards.size(); i++) {
            RectF rect = new RectF(left, firstTop + i * overlap, left + cardWidth,
                    firstTop + i * overlap + cardHeight);
            drawCard(canvas, cards.get(i), rect, true, i == cards.size() - 1);
        }
    }

    private List<Card> draggedCards() {
        ArrayList<Card> result = new ArrayList<>();
        if (pointerSource == null) return result;
        if (pointerSource.type == Location.Type.TABLEAU) {
            List<Card> column = game.getColumn(pointerSource.pile);
            if (pointerSource.cardIndex >= 0 && pointerSource.cardIndex < column.size()) {
                result.addAll(column.subList(pointerSource.cardIndex, column.size()));
            }
        } else if (pointerSource.type == Location.Type.FREE_CELL) {
            Card card = game.getFreeCell(pointerSource.pile);
            if (card != null) result.add(card);
        }
        return result;
    }

    private void drawCelebration(Canvas canvas) {
        if (celebrationStarted == 0L || !celebration) return;
        long age = SystemClock.elapsedRealtime() - celebrationStarted;
        if (age > 4500L) {
            celebrationStarted = 0L;
            return;
        }
        paint.setColor(Color.argb(130, 0, 0, 0));
        canvas.drawRect(0, getHeight() * .34f, getWidth(), getHeight() * .57f, paint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(GOLD);
        textPaint.setTextSize(dp(34));
        canvas.drawText("ALL CLEARED!", getWidth() / 2f, getHeight() * .45f, textPaint);
        textPaint.setTextSize(dp(15));
        textPaint.setColor(Color.WHITE);
        canvas.drawText("Parabéns — jogo concluído", getWidth() / 2f, getHeight() * .50f, textPaint);
        for (int i = 0; i < 26; i++) {
            float x = (i * 89f + age * (i % 3 + 1) * .08f) % getWidth();
            float y = (i * 53f + age * (i % 4 + 1) * .12f) % getHeight();
            paint.setColor(i % 2 == 0 ? GOLD : Color.WHITE);
            canvas.drawCircle(x, y, dp(3), paint);
        }
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = dragX = x;
                downY = dragY = y;
                pointerSource = locationAt(x, y, false);
                dragging = false;
                lastInteraction = SystemClock.elapsedRealtime();
                return true;
            case MotionEvent.ACTION_MOVE:
                dragX = x;
                dragY = y;
                if (pointerSource != null && pointerSource.type != Location.Type.FOUNDATION
                        && Math.hypot(x - downX, y - downY) > dp(8)) {
                    if (!dragging && vibration) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    dragging = true;
                    selected = pointerSource;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                pointerSource = null;
                dragging = false;
                selected = null;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
                lastInteraction = SystemClock.elapsedRealtime();
                if (dragging && pointerSource != null) {
                    Location source = pointerSource;
                    Location destination = locationAt(x, y, true);
                    boolean moved = destination != null && makeMove(source, destination);
                    pointerSource = null;
                    dragging = false;
                    selected = null;
                    if (!moved) rejectMove(source);
                    invalidate();
                    return true;
                }
                pointerSource = null;
                dragging = false;
                int control = controlAt(x, y);
                if (control >= 0) {
                    handleControl(control);
                    return true;
                }
                Location tapped = locationAt(x, y, false);
                if (tapped != null) handleTap(tapped);
                else {
                    selected = null;
                    invalidate();
                }
                return true;
            default:
                return true;
        }
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    private int controlAt(float x, float y) {
        for (int visual = 0; visual < controls.length; visual++) {
            if (controls[visual] != null && controls[visual].contains(x, y)) {
                return leftHanded ? controls.length - 1 - visual : visual;
            }
        }
        return -1;
    }

    private Location locationAt(float x, float y, boolean destination) {
        for (int visual = 0; visual < topSlots.length; visual++) {
            if (topSlots[visual] != null && topSlots[visual].contains(x, y)) {
                int logical = slotLogicalIndex(visual);
                return logical < 4 ? Location.freeCell(logical) : Location.foundation(logical - 4);
            }
        }
        if (y < tableauTop - dp(5) || y > tableauBottom + cardHeight) return null;
        int visualCol = (int) ((x - boardLeft) / (cardWidth + gap));
        if (visualCol < 0 || visualCol >= 8) return null;
        int col = leftHanded ? 7 - visualCol : visualCol;
        List<Card> cards = game.getColumn(col);
        if (destination || cards.isEmpty()) return Location.tableau(col, Math.max(0, cards.size() - 1));
        int index = Math.min(cards.size() - 1, Math.max(0, (int) ((y - tableauTop) / overlap)));
        return Location.tableau(col, index);
    }

    private void handleControl(int action) {
        if (action == 0) listener.onSettingsRequested();
        else if (action == 1) listener.onThemeRequested();
        else if (action == 2) listener.onNewGameRequested();
        else if (action == 3) showHint(true);
        else if (game.undo()) {
            selected = null;
            successFeedback();
            listener.onStateChanged();
            recalculateOverlap();
            invalidate();
        }
    }

    private void showHint(boolean toast) {
        Hint hint = game.findHint();
        if (hint == null) {
            if (toast) Toast.makeText(getContext(), R.string.no_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        selected = hint.from;
        String message = portugueseHint(hint);
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
        announceForAccessibility(message);
        invalidate();
    }

    private String portugueseHint(Hint hint) {
        Card card = game.cardAt(hint.from);
        String name = card == null ? "a carta" : card.label();
        if (hint.to.type == Location.Type.FOUNDATION) return "Mova " + name + " para a fundação";
        if (hint.to.type == Location.Type.FREE_CELL) return "Mova " + name + " para uma célula livre";
        return "Mova " + name + " para a coluna " + (hint.to.pile + 1);
    }

    private void handleTap(Location tapped) {
        Card card = game.cardAt(tapped);
        if (selected == null) {
            if (card == null || tapped.type == Location.Type.FOUNDATION) return;
            if (tapMove) {
                if (smartMove(tapped)) return;
                rejectMove(tapped);
                return;
            }
            if (quickGame && tryFoundation(tapped)) return;
            selected = tapped;
            invalidate();
            return;
        }
        if (selected.equals(tapped)) {
            if (tapMove && tryFoundation(selected)) return;
            selected = null;
            invalidate();
            return;
        }
        if (makeMove(selected, tapped)) {
            selected = null;
            return;
        }
        Location rejected = selected;
        selected = null;
        rejectMove(rejected);
    }

    private boolean smartMove(Location from) {
        Card card = game.cardAt(from);
        if (card == null || from.type == Location.Type.FOUNDATION) return false;
        if (makeMove(from, Location.foundation(card.suit.ordinal()))) return true;

        for (int emptyPass = 0; emptyPass < 2; emptyPass++) {
            for (int column = 0; column < 8; column++) {
                if (from.type == Location.Type.TABLEAU && from.pile == column) continue;
                boolean empty = game.getColumn(column).isEmpty();
                if ((emptyPass == 0 && empty) || (emptyPass == 1 && !empty)) continue;
                if (makeMove(from, Location.tableau(column,
                        Math.max(0, game.getColumn(column).size() - 1)))) return true;
            }
        }

        if (from.type == Location.Type.TABLEAU) {
            for (int cell = 0; cell < 4; cell++) {
                if (game.getFreeCell(cell) == null && makeMove(from, Location.freeCell(cell))) return true;
            }
        }
        return false;
    }

    private void rejectMove(Location source) {
        selected = null;
        invalidLocation = source;
        invalidStarted = SystemClock.elapsedRealtime();
        errorFeedback();
        invalidate();
    }

    private boolean tryFoundation(Location from) {
        Card card = game.cardAt(from);
        return card != null && makeMove(from, Location.foundation(card.suit.ordinal()));
    }

    private boolean makeMove(Location from, Location to) {
        if (!game.move(from, to)) return false;
        successFeedback();
        listener.onStateChanged();
        recalculateOverlap();
        invalidate();
        if (game.isWon()) reportWin();
        else if (autoComplete) postDelayed(this::autoCompleteOne, 190L);
        return true;
    }

    private void autoCompleteOne() {
        if (!autoComplete || dragging || game.isWon()) return;
        Hint move = game.findFoundationMove();
        if (move != null && game.move(move.from, move.to)) {
            listener.onStateChanged();
            recalculateOverlap();
            successFeedback();
            invalidate();
            if (game.isWon()) reportWin();
            else postDelayed(this::autoCompleteOne, 190L);
        }
    }

    private void reportWin() {
        if (winReported) return;
        winReported = true;
        pauseClock();
        celebrationStarted = SystemClock.elapsedRealtime();
        if (sound) tone.startTone(ToneGenerator.TONE_PROP_ACK, 350);
        if (vibration) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        invalidate();
        postDelayed(listener::onGameWon, celebration ? 1400L : 100L);
    }

    private boolean isDragged(Location location) {
        return dragging && pointerSource != null && pointerSource.equals(location);
    }

    private void successFeedback() {
        if (sound) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 55);
        if (vibration) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void errorFeedback() {
        if (sound) tone.startTone(ToneGenerator.TONE_PROP_NACK, 80);
        if (vibration) performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private static String formatTime(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private float dp(float value) { return value * density; }
}
