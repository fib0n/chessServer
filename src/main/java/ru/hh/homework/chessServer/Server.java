package ru.hh.homework.chessServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;

class Server implements Runnable {
    private final ServerSocketChannel serverSocket;
    private final Selector selector;
    private final ByteBuffer inputBuffer;
    private final ByteBuffer outputBuffer;
    private final Charset charset;

    private final ConcurrentMap<String, SocketChannel> socketChannels;
    private final BlockingQueue<Exchange> requestQueue;

    public Server(final int port) throws IOException {
        this.socketChannels = new ConcurrentHashMap<>();
        this.requestQueue = new LinkedBlockingQueue<>();

        this.inputBuffer = ByteBuffer.allocateDirect(Settings.BUFFER_SIZE);
        this.outputBuffer = ByteBuffer.allocateDirect(Settings.BUFFER_SIZE);
        this.charset = StandardCharsets.US_ASCII;

        this.serverSocket = ServerSocketChannel.open();
        this.serverSocket.socket().bind(new InetSocketAddress(port));
        this.serverSocket.configureBlocking(false);
        this.selector = Selector.open();
        this.serverSocket.register(this.selector, SelectionKey.OP_ACCEPT);
    }

    public synchronized void write(final Exchange response) {
        if (!this.socketChannels.containsKey(response.address))
            return;

        if (response.body.equals(Settings.Command.CLOSE.name())) {
            closeChannel(response.address, false);
            return;
        }

        this.outputBuffer.clear();
        this.outputBuffer.put((response.body + "\n").getBytes(this.charset));
        this.outputBuffer.flip();
        try {
            this.socketChannels.get(response.address).write(this.outputBuffer);
        } catch (IOException e) {
            closeChannel(response.address, true);
            e.printStackTrace();
        }
    }

    public Exchange takeRequest() throws InterruptedException {
        return this.requestQueue.take();
    }

    @Override
    public void run() {

        System.out.println("ru.hh.homework.chessServer.Server started on port: "
                + this.serverSocket.socket().getLocalPort());

        while (!Thread.interrupted()) {
            try {
                final int ready = this.selector.select(Settings.TIMEOUT);
                if (ready == 0) {
                    continue;
                }

                final Iterator<SelectionKey> selectedKeys
                        = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    final SelectionKey key = selectedKeys.next();
                    selectedKeys.remove();

                    if (!key.isValid()) {
                        System.err.println("Key is not valid " + key);
                    } else if (key.isAcceptable()) {
                        this.accept(key);
                    } else if (key.isReadable()) {
                        this.read(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void accept(final SelectionKey key) throws IOException {
        final ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        final SocketChannel socketChannel = serverSocketChannel.accept();
        if (socketChannel != null) {
            socketChannel.configureBlocking(false);
            socketChannel.register(this.selector, SelectionKey.OP_READ);

            final String address = socketChannel.getRemoteAddress().toString();
            this.socketChannels.put(address, socketChannel);
            System.out.printf("Connected %s\n", address);
        }
    }

    private void read(final SelectionKey key) {
        this.inputBuffer.clear();
        final SocketChannel socketChannel = (SocketChannel) key.channel();
        String address = null;
        try {
            address = socketChannel.getRemoteAddress().toString();
            final int byteCount = socketChannel.read(this.inputBuffer);
            this.inputBuffer.flip();
            if (byteCount == -1) {
                key.cancel();
                this.closeChannel(address, socketChannel, true);
            }
        } catch (IOException e) {
            key.cancel();
            this.closeChannel(address, socketChannel, true);
            this.inputBuffer.clear();
            e.printStackTrace();
            return;
        }
        if (this.inputBuffer.limit() > 0) {
            final CharBuffer charBuffer = this.charset.decode(this.inputBuffer);
            this.requestQueue.add(new Exchange(address, charBuffer.toString()));
        }
        this.inputBuffer.clear();
    }

    private void closeChannel(final String address, boolean withRequest) {
        if (this.socketChannels.containsKey(address))
            closeChannel(address, this.socketChannels.get(address), withRequest);
    }

    private void closeChannel(final String address, final SocketChannel socketChannel, boolean withRequest) {
        if (socketChannel != null) {
            try {
                socketChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (address != null) {
                    this.socketChannels.remove(address);
                    System.out.printf("Disconnected %s\n", address);
                    if (withRequest)
                        this.requestQueue.add(new Exchange(address, Settings.Command.CLOSE.name()));
                } else
                    this.socketChannels.values().remove(socketChannel);
            }
        }
    }
}
