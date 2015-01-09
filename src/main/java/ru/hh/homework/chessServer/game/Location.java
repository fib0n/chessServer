package ru.hh.homework.chessServer.game;

import java.util.Optional;

import static com.google.common.base.Preconditions.checkState;

public class Location {
    public final int x;
    public final int y;

    private Location(final int x, final int y) {
        checkState(isValid(x, y), "Location is out of bounds");
        this.x = x;
        this.y = y;
    }

    public static Optional<Location> tryParse(final String s) {
        if (!isValid(s))
            return Optional.empty();

        final int x = s.charAt(1) - '1';
        final int y = s.charAt(0) - 'a';
        if (!isValid(x, y))
            return Optional.empty();

        return Optional.of(new Location(x, y));
    }

    private static boolean isValid(final String s) {
        return s.matches("[a-h][1-8]");
    }

    private static boolean isValid(final int x, final int y) {
        return x >= 0 && x < Board.ROWS && y >= 0 && y < Board.COLUMNS;
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        final Location location = (Location) that;
        return this.x == location.x && this.y == location.y;
    }

    @Override
    public String toString() {
        return String.valueOf((char) (this.y + 'a')) + String.valueOf(this.x + 1);
    }
}
