package ru.hh.homework.chessServer;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import ru.hh.homework.chessServer.game.Game;
import ru.hh.homework.chessServer.game.MoveResult;
import ru.hh.homework.chessServer.game.Player;
import ru.hh.homework.chessServer.game.Status;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

class Worker implements Runnable {

    private final Server server;
    private final BiMap<String, Player> sessions; //socket <-> player
    private final Map<UUID, Game> games; //gameId -> game;

    public Worker(final Server server) {
        this.server = checkNotNull(server);
        this.sessions = HashBiMap.create();
        this.games = new HashMap<>();
        loadUnFinishedGames();
    }

    @Override
    public void run() {
        while (!Thread.interrupted()) {
            try {
                final Exchange request = this.server.takeRequest();
                process(request).forEach(this.server::write);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private List<Exchange> process(final Exchange request) {
        final List<Exchange> exchanges = new ArrayList<>();
        final String[] args = request.body.split("\\s+");
        if (args.length > 0) {
            final Optional<Settings.Command> commandMaybe = Settings.Command.tryParse(args[0]);
            if (commandMaybe.isPresent()) {
                switch (commandMaybe.get()) {
                    case CREATE:
                        exchanges.add(create(request.address, args));
                        break;
                    case JOIN:
                        exchanges.addAll(join(request.address, args));
                        break;
                    case MOVE:
                        exchanges.addAll(move(request.address, args));
                        break;
                    case PRINT_STATE:
                        exchanges.add(printState(request.address));
                        break;
                    case EXIT:
                        exchanges.addAll(exit(request.address));
                        break;
                    case CLOSE:
                        close(request.address);
                        return exchanges;
                }
            }
        }
        if (exchanges.isEmpty())
            exchanges.add(new Exchange(request.address, "Unknown command"));
        return exchanges;
    }

    private Exchange create(final String address, final String[] args) {
        final boolean isWhite = args.length <= 1 || !Boolean.parseBoolean(args[1]);
        final UUID gameId = UUID.randomUUID();
        final UUID token = UUID.randomUUID();

        final Game game = new Game(gameId);
        final Player player = new Player(game, token.toString(), isWhite);
        game.addPlayer(player);
        this.sessions.forcePut(address, player);
        this.games.put(gameId, game);

        return new Exchange(address, String.format("GameId: %s\nToken: %s (use if connection fails)%s",
                gameId, token, game.printState(isWhite)));
    }

    private List<Exchange> join(final String address, final String[] args) {
        final List<Exchange> exchanges = new ArrayList<>();

        if (args.length <= 1) {
            exchanges.add(new Exchange(address, "Usage 'join gameId token (optional, use if connection fails)'"));
            return exchanges;
        }
        final Optional<UUID> gameIdMaybe = tryParseUUID(args[1]);
        if (!gameIdMaybe.isPresent()) {
            exchanges.add(new Exchange(address, "Incorrect gameId"));
            return exchanges;
        }

        final UUID gameId = gameIdMaybe.get();
        if (!this.games.containsKey(gameId)) {
            exchanges.add(new Exchange(address, "Game was not found"));
            return exchanges;
        }

        final Game game = this.games.get(gameId);
        final Player currentPlayer;
        final String token;
        if (game.onlyOnePlayer()) {
            if (address.equals(this.sessions.inverse().get(game.getPlayerFirst()))) {
                exchanges.add(new Exchange(address, "You are trying to play with yourself"));
                return exchanges;
            }
            token = UUID.randomUUID().toString();
            currentPlayer = new Player(game, token, game.getFreeColor());
            game.addPlayer(currentPlayer);
            this.sessions.forcePut(address, currentPlayer);
        } else {
            if (args.length <= 2) {
                exchanges.add(new Exchange(address, "The game has already begun, use input token to connect"));
                return exchanges;
            }
            final Optional<UUID> tokenMaybe = tryParseUUID(args[2]);
            if (!tokenMaybe.isPresent()) {
                exchanges.add(new Exchange(address, "Incorrect token"));
                return exchanges;
            }

            token = tokenMaybe.get().toString();
            final Optional<Player> currentPlayerMaybe = game.getPlayer(token, false);
            if (!currentPlayerMaybe.isPresent()) {
                exchanges.add(new Exchange(address, "Game is inaccessible"));
                return exchanges;
            }
            currentPlayer = currentPlayerMaybe.get();
            final BiMap<Player, String> inverse = this.sessions.inverse();
            if (address.equals(inverse.get(game.getPlayer(token, true).get()))) {
                exchanges.add(new Exchange(address, "You are trying to play with yourself"));
                return exchanges;
            }
            final String oldAddress = inverse.remove(currentPlayer);
            if (oldAddress != null) {
                exchanges.add(new Exchange(oldAddress, Settings.Command.CLOSE.name()));
            }
        }
        this.sessions.forcePut(address, currentPlayer);
        exchanges.add(new Exchange(address, String.format("Token: %s (use if connection fails)%s",
                token, game.printState(currentPlayer.isWhite))));
        return exchanges;
    }

    private Optional<UUID> tryParseUUID(final String s) {
        UUID id;
        try {
            id = UUID.fromString(s);
            return Optional.of(id);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private List<Exchange> move(final String address, final String[] args) {
        final List<Exchange> exchanges = new ArrayList<>();
        if (args.length <= 2) {
            exchanges.add(new Exchange(address, "Usage 'move f7 f6'"));
            return exchanges;
        }
        if (!this.sessions.containsKey(address)) {
            exchanges.add(new Exchange(address, "Create or join a game before making a move"));
            return exchanges;
        }
        final Player currentPlayer = this.sessions.get(address);
        final Game game = currentPlayer.game;
        if (game.onlyOnePlayer()) {
            exchanges.add(new Exchange(address, "Only one player in the game"));
            return exchanges;
        }
        final MoveResult result = game.move(currentPlayer.token, args[1], args[2]);
        if (result.status == Status.INCORRECT) {
            exchanges.add(new Exchange(address, result.toString()));
            return exchanges;
        }
        exchanges.add(new Exchange(address, result + game.printState(currentPlayer.isWhite)));
        final Player oppositePlayer = game.getPlayer(currentPlayer.token, true).orElse(currentPlayer);
        final String addressOpposite = this.sessions.inverse().get(oppositePlayer);
        exchanges.add(new Exchange(addressOpposite, result + game.printState(oppositePlayer.isWhite)));

        if (result.status == Status.WHITE_WINS || result.status == Status.BLACK_WINS) {
            close(address);
            close(addressOpposite);
            dispose(game.id);
            return exchanges;
        }
        saveGame(game);
        return exchanges;
    }

    private Exchange printState(final String address) {
        if (!this.sessions.containsKey(address)) {
            return new Exchange(address, "Create or join a game before printing");
        }
        final Player player = this.sessions.get(address);
        return new Exchange(address, player.game.printState(player.isWhite));
    }

    private void dispose(UUID gameId) {
        this.games.remove(gameId);
        try {
            Files.deleteIfExists(Paths.get(String.format("%s/%s%s",
                    Settings.GAME_FOLDER_NAME, gameId, Settings.GAME_FILE_EXTENSION)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void close(final String address) {
        this.sessions.remove(address);
        //warn! tokens and games aren't removed so that they can be restored later
    }

    private List<Exchange> exit(final String address) {
        final List<Exchange> exchanges = new ArrayList<>();
        if (!this.sessions.containsKey(address))
            return exchanges;

        final Player currentPlayer = this.sessions.remove(address);
        exchanges.add(new Exchange(address, "You have left the game"));
        exchanges.add(new Exchange(address, Settings.Command.CLOSE.name()));
        final Game game = currentPlayer.game;
        dispose(game.id);

        final Player oppositePlayer = game.getPlayer(currentPlayer.token, true).orElse(currentPlayer);
        final String addressOpposite = this.sessions.inverse().remove(oppositePlayer);

        if (addressOpposite != null && !address.equals(addressOpposite)) {
            exchanges.add(new Exchange(addressOpposite,
                    String.format("%s exits. Create or join a new game", currentPlayer)));
        }
        return exchanges;
    }


    private void loadUnFinishedGames() {
        try {
            final File directory = new File(Settings.GAME_FOLDER_NAME);
            if (!directory.mkdir()) {
                for (final File file : directory.listFiles()) {
                    if (file.isFile() && file.getName().endsWith(Settings.GAME_FILE_EXTENSION)) {
                        final Game game = Utils.deserialize(Paths.get(file.getPath()));
                        if (game != null && game.id != null)
                            games.put(game.id, game);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveGame(final Game game) {
        Utils.serialize(game, Paths.get(String.format("%s/%s%s",
                Settings.GAME_FOLDER_NAME, game.id, Settings.GAME_FILE_EXTENSION)));
    }
}
