package ru.hh.homework.chessServer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

final class Utils {
    private Utils() {
    }

    public static <T extends Serializable> void serialize(final T object, final Path path) {
        try (OutputStream outputStream = Files.newOutputStream(path);
             ObjectOutputStream oos = new ObjectOutputStream(outputStream)) {
            oos.writeObject(object);
        } catch (IOException e) {
            e.printStackTrace();
        }

        //uses buffer
        /*
        try (OutputStream file = new FileOutputStream(path, false);
             OutputStream buffer = new BufferedOutputStream(file);
             ObjectOutput output = new ObjectOutputStream(buffer)) {
            output.writeObject(object);
        } catch (IOException e) {
            System.out.println(e);
        }
        */
    }

    @SuppressWarnings("unchecked")
    public static <T extends Serializable> T deserialize(final Path path) {
        try (InputStream inputStream = Files.newInputStream(path);
             ObjectInputStream ois = new ObjectInputStream(inputStream)) {
            return (T) ois.readObject();
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }
}
