package ru.hh.homework.chessServer.game;

public class MoveResult {
    public final Status status;
    private final String message;

    public MoveResult(final String message, final Status status) {
        this.message = message;
        this.status = status;
    }

    public MoveResult(final boolean isWhite) {
        this.message = (isWhite ? "White" : "Black") + " wins";
        this.status = isWhite ? Status.WHITE_WINS : Status.BLACK_WINS;
    }

    @Override
    public String toString() {
        if (this.status == Status.WHITE_WINS || this.status == Status.BLACK_WINS)
            return String.format("%s\nGame over", this.message);
        return this.message;
    }
}


