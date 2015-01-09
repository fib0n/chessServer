package ru.hh.homework.chessServer;

import static com.google.common.base.Preconditions.checkNotNull;

public class Exchange {
    public final String address;
    public final String body;

    public Exchange(final String address, final String body) {
        this.address = checkNotNull(address);
        this.body = body;
    }
}
