package com.example.ipcamcamerax2;

import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class MJPEGServer extends Thread {

    private volatile byte[] latestJpeg;
    private volatile boolean running = true;

    private ServerSocket serverSocket;

    private static final int FRAME_INTERVAL_MS = 33;

    public void updateJpeg(byte[] jpegData) {
        latestJpeg = jpegData;
    }

    @Override
    public void run() {

        try {

            serverSocket = new ServerSocket(8989);

            while (running) {
                Socket client = serverSocket.accept();
                new Thread(() -> streamToClient(client)).start();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void streamToClient(Socket client) {

        try (OutputStream outputStream =
                     client.getOutputStream()) {

            String boundary = "MJPEGBOUNDARY";

            outputStream.write((
                    "HTTP/1.0 200 OK\r\n" +
                            "Server: IPCamServer\r\n" +
                            "Connection: close\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Pragma: no-cache\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=" +
                            boundary +
                            "\r\n\r\n"
            ).getBytes());

            while (running && !client.isClosed()) {

                byte[] jpeg = latestJpeg;

                if (jpeg != null) {

                    outputStream.write(
                            ("--" + boundary + "\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Length: " +
                                    jpeg.length +
                                    "\r\n\r\n").getBytes());

                    outputStream.write(jpeg);
                    outputStream.write("\r\n".getBytes());

                    outputStream.flush();
                }

                Thread.sleep(FRAME_INTERVAL_MS);
            }

        } catch (Exception ignored) {
        }
    }

    public void stopServer() {

        running = false;

        try {

            if (serverSocket != null &&
                    !serverSocket.isClosed()) {
                serverSocket.close();
            }

        } catch (Exception ignored) {
        }
    }
}