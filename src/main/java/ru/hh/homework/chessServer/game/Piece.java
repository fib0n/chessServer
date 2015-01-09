package ru.hh.homework.chessServer.game;

public enum Piece {
    ROOK("r"),
    KNIGHT("n"),
    BISHOP("b"),
    KING("k"),
    QUEEN("q"),
    PAWN("p");

    public final String abbr;

    private Piece(String name) {
        this.abbr = name;
    }

    public char getAbbrChar() {
        return this.abbr.charAt(0);
    }
}
