package ru.hh.homework.chessServer;

import java.io.IOException;

class Main {
    public static void main(final String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java chessServer port");
            return;
        }
        final int port = Integer.parseInt(args[0]);
        System.out.println("Starting chess server ...");

        final Server server = new Server(port);
        new Thread(server).start();
        new Thread(new Worker(server)).start();
    }
}
