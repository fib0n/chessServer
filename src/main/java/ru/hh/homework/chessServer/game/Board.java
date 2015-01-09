package ru.hh.homework.chessServer.game;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;

import static com.google.common.base.Preconditions.checkState;

class Board implements Serializable {
    public static final int ROWS = 8;
    public static final int COLUMNS = 8;

    private static final Map<String, Predicate<Movement>> rules =
            new HashMap<String, Predicate<Movement>>() {{
                put(Piece.BISHOP.abbr, m -> Math.abs(m.start.x - m.end.x) == Math.abs(m.start.y - m.end.y));
                put(Piece.KING.abbr, m -> Math.abs(m.end.x - m.start.x) <= 1 && Math.abs(m.end.y - m.start.y) <= 1);
                put(Piece.KNIGHT.abbr, m -> Math.abs((m.end.x - m.start.x) * (m.end.y - m.start.y)) == 2);
                put(Piece.QUEEN.abbr, m -> m.end.x == m.start.x || m.end.y == m.start.y
                        || Math.abs(m.start.x - m.end.x) == Math.abs(m.start.y - m.end.y));
                put(Piece.ROOK.abbr, m -> m.end.x == m.start.x || m.end.y == m.start.y);
                put(Piece.PAWN.abbr, m -> {
                    final int direction = m.isWhite ? 1 : -1;
                    return m.isCapture && m.end.x == m.start.x + direction && (Math.abs(m.start.y - m.end.y) == 1)
                            || !m.isCapture && m.start.y == m.end.y
                            && (m.end.x == m.start.x + direction || m.end.x == m.start.x + 2 * direction
                            && m.start.x == (ROWS - 1 + direction) % (ROWS - 1));
                });
            }};
    private final char[][] state;

    public Board() {
        this.state = new char[][]{
                {'R', 'N', 'B', 'K', 'Q', 'B', 'N', 'R'},
                {'P', 'P', 'P', 'P', 'P', 'P', 'P', 'P'},
                {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
                {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
                {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
                {' ', ' ', ' ', ' ', ' ', ' ', ' ', ' '},
                {'p', 'p', 'p', 'p', 'p', 'p', 'p', 'p'},
                {'r', 'n', 'b', 'k', 'q', 'b', 'n', 'r'}
        };
    }

    private boolean isEmpty(int x, int y) {
        final char piece = this.state[x][y];
        return isEmpty(piece);
    }

    private boolean isEmpty(final char piece) {
        return piece == ' ';
    }

    private boolean isWhite(final char piece) {
        return Character.isLetter(piece) && Character.isUpperCase(piece);
    }

    private boolean isBlack(final char piece) {
        return Character.isLetter(piece) && !isWhite(piece);
    }

    private String getColorName(final boolean isWhite) {
        return isWhite ? "White" : "Black";
    }

    public MoveResult move(final boolean isWhite, final Location start, final Location end) {
        final char initial = this.state[start.x][start.y];
        if (isEmpty(initial) || isWhite(initial) != isWhite)
            return new MoveResult(
                    String.format("No %s piece at the initial square", getColorName(isWhite)),
                    Status.INCORRECT);

        final char targetPiece = this.state[end.x][end.y];
        final boolean isCapture = !isEmpty(targetPiece);
        if (isCapture && isWhite(targetPiece) == isWhite) {
            return new MoveResult("The target position is occupied with a piece of your own color",
                    Status.INCORRECT);
        }
        final Movement movement;
        try {
            movement = new Movement(isCapture, isWhite, start, end);
        } catch (Exception e) {
            return new MoveResult("Current position and target position are equal", Status.INCORRECT);
        }
        if (!(rules.getOrDefault(String.valueOf(Character.toLowerCase(initial)), m -> false).test(movement))) {
            return new MoveResult("The piece cannot move to the target square", Status.INCORRECT);
        }

        if (!(initial == Piece.KNIGHT.getAbbrChar()
                || isFreePath(movement.start, movement.end))) {
            return new MoveResult("The path from the initial to the target square is not free.", Status.INCORRECT);
        }
        //todo castling etc

        this.state[movement.end.x][movement.end.y] = initial;
        this.state[movement.start.x][movement.start.y] = ' ';

        boolean withPromotion = tryPromotion(movement, initial);
        return new MoveResult(
                movement.getDescription(getPieceRepresentation(initial), getPieceRepresentation(targetPiece))
                        + (withPromotion ? " with promotion to queen" : ""), Status.CORRECT);
    }

    public boolean pieceExists(final boolean isWhite, final char piece) {
        for (int i = 0; i < ROWS; ++i) {
            for (int j = 0; j < COLUMNS; ++j) {
                final char currentPiece = this.state[i][j];
                if (isWhite == isWhite(currentPiece) && piece == Character.toLowerCase(currentPiece)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean tryPromotion(final Movement movement, char initial) {
        if (isBlack(initial) && movement.end.x == 0 && initial == Piece.PAWN.getAbbrChar()) {
            this.state[movement.end.x][movement.end.y] = Piece.QUEEN.getAbbrChar();
            return true;
        }
        return false;
    }

    private int sign(final int a) {
        return a > 0 ? 1 : a < 0 ? -1 : 0;
    }

    private boolean isFreePath(final Location start, final Location end) {
        final int stepX = sign(end.x - start.x);
        final int stepY = sign(end.y - start.y);

        int x = start.x + stepX;
        int y = start.y + stepY;
        while (!(x == end.x && y == end.y)) {
            if (!isEmpty(x, y))
                return false;
            x += stepX;
            y += stepY;
        }
        return true;
    }

    public String print(final boolean isWhite) {
        final StringBuilder result = new StringBuilder();
        result.append("\n   ---------------------------------\n");
        for (int i = 0; i < ROWS; ++i) {
            if (i > 0) {
                result.append("\n   |---+---+---+---+---+---+---+---|\n");
            }
            final int actualRow = isWhite ? ROWS - i - 1 : i;
            result.append(String.format("%d  |", actualRow + 1));
            for (int j = 0; j < COLUMNS; ++j) {
                final int actualColumn = isWhite ? j : COLUMNS - j - 1;
                result.append(String.format(" %s|", getPieceRepresentation(this.state[actualRow][actualColumn])));
            }
        }
        result.append("\n   ---------------------------------\n");
        if (isWhite)
            result.append("     a   b   c   d   e   f   g   h\n");
        else
            result.append("     h   g   f   e   d   c   b   a\n");
        return result.toString();
    }

    private String getPieceRepresentation(char ch) {
        if (isBlack(ch))
            return "*" + Character.toUpperCase(ch);
        return ch + " ";
    }

    private class Movement {
        public final boolean isWhite;
        public final boolean isCapture;
        public final Location start;
        public final Location end;

        public Movement(final boolean isCapture, final boolean isWhite,
                        final Location start, final Location end) {

            checkState(start != end, "Current position and target position are equal");

            this.isCapture = isCapture;
            this.isWhite = isWhite;
            this.start = start;
            this.end = end;
        }

        public String getDescription(final String piece, final String targetPiece) {
            return String.format("%s (%s) %s: '%s - %s'",
                    getColorName(this.isWhite),
                    piece,
                    this.isCapture ? "attacks (" + targetPiece + ")" : "moves",
                    this.start, this.end);
        }
    }
}
