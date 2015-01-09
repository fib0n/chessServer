package ru.hh.homework.chessServer;

import java.util.Optional;

public final class Settings {
    public static final int BUFFER_SIZE = 2 << 13;
    public static final int TIMEOUT = 1000;

    public static final String GAME_FOLDER_NAME = "data";
    public static final String GAME_FILE_EXTENSION = ".game";

    private Settings() {
    }

    public enum Command {
        CREATE,
        JOIN,
        MOVE,
        PRINT_STATE,
        EXIT,
        CLOSE; //server command

        public static Optional<Command> tryParse(final String s) {
            if (s != null) {
                for (Command c : Command.values()) {
                    if (s.equalsIgnoreCase(c.name())) {
                        return Optional.of(c);
                    }
                }
            }
            return Optional.empty();
        }
    }
}
