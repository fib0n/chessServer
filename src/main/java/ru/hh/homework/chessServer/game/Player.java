package ru.hh.homework.chessServer.game;

import java.io.Serializable;

import static com.google.common.base.Preconditions.checkNotNull;

public class Player implements Serializable {
    public final String token;
    public final boolean isWhite;
    public final Game game;

    public Player(final Game game, final String token, final boolean isWhite) {
        this.game = checkNotNull(game);
        this.token = checkNotNull(token);
        this.isWhite = isWhite;
    }

    @Override
    public int hashCode() {
        return this.token.hashCode();
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        final Player player = (Player) that;
        return this.token.equals(player.token);
    }

    @Override
    public String toString() {
        return String.format("%s player", isWhite ? "White" : "Black");
    }
}
