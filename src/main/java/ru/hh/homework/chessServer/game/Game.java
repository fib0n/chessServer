package ru.hh.homework.chessServer.game;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkState;

public class Game implements Serializable {
    public final UUID id;
    private final List<Player> players = new ArrayList<>(2);
    private final Board board = new Board();
    private boolean currentTurn;

    public Game(final UUID id) {
        this.id = id;
        this.currentTurn = true; //true - white, false - black
    }

    public MoveResult move(final String login, final String fieldFrom, final String fieldTo) {
        checkState(this.players.size() == 2);
        final Optional<Player> playerMaybe = getPlayer(login, false);
        if (!playerMaybe.isPresent()) {
            return new MoveResult("Unknown player", Status.INCORRECT);
        }
        final Player player = playerMaybe.get();
        if (player.isWhite != this.currentTurn) {
            return new MoveResult("Wait for your turn", Status.INCORRECT);
        }
        final Optional<Location> start = Location.tryParse(fieldFrom);
        final Optional<Location> end = Location.tryParse(fieldTo);
        if (start.isPresent() && end.isPresent()) {
            final MoveResult result = this.board.move(player.isWhite, start.get(), end.get());
            if (result.status == Status.CORRECT) {
                this.currentTurn = !this.currentTurn;
            }
            if (result.status == Status.CORRECT && isPlayerWin(player.isWhite))
                return new MoveResult(player.isWhite);
            return result;
        }
        return new MoveResult("Start or end location is incorrect", Status.INCORRECT);
    }

    public void addPlayer(final Player player) {
        if (players.size() < 2) {
            this.players.add(player);
        }
    }

    public boolean onlyOnePlayer() {
        return this.players.size() == 1;
    }

    public boolean getFreeColor() {
        checkState(this.players.size() == 1);
        return !this.players.get(0).isWhite;
    }

    public Player getPlayerFirst() {
        checkState(this.players.size() == 1);
        return this.players.get(0);
    }

    public Optional<Player> getPlayer(final String token, final boolean opposite) {
        checkState(this.players.size() == 2);
        if (token.equals(this.players.get(0).token))
            return Optional.of(this.players.get(opposite ? 1 : 0));
        if (token.equals(this.players.get(1).token))
            return Optional.of(this.players.get(opposite ? 0 : 1));
        return Optional.empty();
    }

    public String printState(final boolean isWhite) {
        String result = "";
        if (onlyOnePlayer() || !isGameOver()) {
            result += String.format("\n%s turn ...\n", this.currentTurn ? "White" : "Black");
        }
        result += this.board.print(isWhite);

        return result;
    }

    @Override
    public int hashCode() {
        return this.id.hashCode();
    }

    @Override
    public boolean equals(final Object that) {
        if (this == that) return true;
        if (that == null || getClass() != that.getClass()) return false;

        final Game game = (Game) that;
        return this.id.equals(game.id);
    }

    private boolean isPlayerWin(final boolean isWhite) {
        checkState(this.players.size() == 2);
        return !this.board.pieceExists(!isWhite, Piece.KING.getAbbrChar());
    }

    private boolean isGameOver() {
        return isPlayerWin(true) || isPlayerWin(false);
    }
}
