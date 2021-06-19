import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;

public class ChatClientObj {
    private static final String sourceDir = Paths.get("").toAbsolutePath().toString() + "\\sources";
    private static Socket client;
    private static BufferedReader sysIn;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static volatile boolean runFlag = true;
    private static final String QUIT = "/q";


    private static class FromServer extends Thread{
        public FromServer(){
            start();
        }
        @Override
        public void run() {
            try {
                Message msg;
                while (runFlag && !client.isClosed()) {
                    msg = (Message)in.readObject();
                    if (msg != null) {
                        System.out.println(parseMessage(msg));
                    }
                }
            } catch (IOException e) {
                runFlag = false;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
            System.out.println("fromServer died.");
        }
        private static String parseMessage(Message msg) {
            StringBuffer result = new StringBuffer();
            if (msg.isSystem()) {
                result.append("Message from server");
            } else {
                result.append(msg.getFromUser());
            }
            result.append(" -> ").append(msg.getToUser()).append(": ").append(msg.getContent());
            return result.toString();
        }
    }
    private static class ToServer extends Thread{
        public ToServer(){
            start();
        }
        @Override
        public void run() {
            try {
                String msgFromConsole;
                while (runFlag) {
                    try {
                        msgFromConsole = sysIn.readLine();
                        Message msg = Message.userMessage(msgFromConsole);
                        if (msg.getCommand().equalsIgnoreCase(QUIT)) {
                            runFlag = false;
                        }
                        out.writeObject(msg);
                        out.flush();
                    } catch (IllegalArgumentException e) {
                        System.out.println(e.getMessage());
                    }
                }
            } catch (IOException e) {
                runFlag = false;
            }
            System.out.println("toServer died.");
        }
    }
    public static void main(String[] args) throws InterruptedException {
        try {
            try{
                client = new Socket("localhost",6666);
                out = new ObjectOutputStream(client.getOutputStream());
                in = new ObjectInputStream(client.getInputStream());
                sysIn = new BufferedReader(new InputStreamReader(System.in));
                FromServer fromServer = new FromServer();
                ToServer toServer = new ToServer();
                toServer.join();
            } finally {
                client.close();
                in.close();
                out.close();
            }
        } catch (Exception e) {}
    }
}