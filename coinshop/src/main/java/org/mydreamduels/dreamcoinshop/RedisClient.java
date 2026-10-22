package org.mydreamduels.dreamcoinshop;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A small Redis client (RESP2) for the handful of commands sync needs. Built in rather than a
 * library, so nothing can clash with other plugins' Redis or logging libraries.
 * Replies: String (simple/bulk), Long (integer), List (array), null (nil); errors throw.
 */
final class RedisClient implements AutoCloseable {

    /** An error reply from Redis (wrong password, script error...). */
    static final class RedisException extends IOException {
        RedisException(String message) {
            super(message);
        }
    }

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    RedisClient(String host, int port, boolean ssl, String username, String password, int database, int timeoutMs)
            throws IOException {
        Socket s = ssl ? SSLSocketFactory.getDefault().createSocket() : new Socket();
        try {
            s.connect(new InetSocketAddress(host, port), timeoutMs);
            s.setSoTimeout(timeoutMs);
            s.setTcpNoDelay(true);
            s.setKeepAlive(true);
        } catch (IOException e) {
            s.close();
            throw e;
        }
        socket = s;
        in = new BufferedInputStream(s.getInputStream());
        out = new BufferedOutputStream(s.getOutputStream());
        try {
            if (password != null && !password.isEmpty()) {
                if (username != null && !username.isEmpty()) call("AUTH", username, password);
                else call("AUTH", password);
            }
            if (database != 0) call("SELECT", String.valueOf(database));
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    /** Sends one command and returns its reply. */
    synchronized Object call(String... args) throws IOException {
        send(args);
        return read();
    }

    /** For subscriptions: send without waiting for a reply. */
    synchronized void send(String... args) throws IOException {
        out.write(('*' + Integer.toString(args.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
        for (String a : args) {
            byte[] b = a.getBytes(StandardCharsets.UTF_8);
            out.write(('$' + Integer.toString(b.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(b);
            out.write('\r');
            out.write('\n');
        }
        out.flush();
    }

    /** Next reply; a {@link SocketTimeoutException} means nothing arrived within the timeout. */
    Object read() throws IOException {
        int type = in.read();
        if (type == -1) throw new IOException("connection closed by Redis");
        String line = readLine();
        switch (type) {
            case '+':
                return line;
            case '-':
                throw new RedisException(line);
            case ':':
                return Long.parseLong(line);
            case '$': {
                int len = Integer.parseInt(line);
                if (len < 0) return null;
                byte[] data = in.readNBytes(len);
                if (data.length < len) throw new IOException("connection closed by Redis");
                readLine(); // trailing CRLF
                return new String(data, StandardCharsets.UTF_8);
            }
            case '*': {
                int n = Integer.parseInt(line);
                if (n < 0) return null;
                List<Object> list = new ArrayList<>(n);
                for (int i = 0; i < n; i++) list.add(read());
                return list;
            }
            default:
                throw new IOException("unexpected reply from Redis: " + (char) type + line);
        }
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(32);
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                int n = in.read();
                if (n == '\n') break;
                buf.write(c);
                if (n == -1) break;
                buf.write(n);
                continue;
            }
            buf.write(c);
        }
        if (c == -1) throw new IOException("connection closed by Redis");
        return buf.toString(StandardCharsets.UTF_8);
    }

    void setTimeout(int ms) throws IOException {
        socket.setSoTimeout(ms);
    }

    boolean isOpen() {
        return !socket.isClosed() && socket.isConnected();
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }
}
