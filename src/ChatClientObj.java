import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;

public class ChatClientObj {
    private static final String sourceDir = Paths.get("").toAbsolutePath().toString() + "\\src\\sources";
    private static Socket client;
    private static BufferedReader sysIn;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static volatile boolean runFlag = true;
//    private static boolean fileFlag = false;
    private static FileConnection fileConnection;

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
                    if (msg.isSystem()) {
                        executeCommand(msg);
                        continue;
                    }
                    if (msg != null) {
                        System.out.println(parseMessage(msg));
                    }
                }
            } catch (IOException e) {
                System.out.println("Something's happened to server. Any interaction will lead to client's closing.");
                runFlag = false;
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        private static void executeCommand(Message msg) {
            if (!msg.getCommand().equalsIgnoreCase(ChatServerObj.ClientHandler.REFUSED)) {
                try {
                    Socket fileSocket = new Socket("localhost", 4004);
                    FileServerEntry fsEntry;
                    if (msg.getCommand().equalsIgnoreCase(ChatServerObj.ClientHandler.UPLOAD)) {
                        fsEntry = FileServerEntry.uploadRequest(msg.getContent());
                    } else {
                        fsEntry = FileServerEntry.downloadRequest(msg.getContent());
                    }
                    fileConnection = new FileConnection(fileSocket, fsEntry);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        private static String parseMessage(Message msg) {
            return msg.getFromUser() + " -> " + msg.getToUser() + ": " + msg.getContent();
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
                        if (msg.getCommand().equalsIgnoreCase(ChatServerObj.ClientHandler.QUIT)) {
                            runFlag = false;
                        }
                        if (msg.getCommand().equalsIgnoreCase(ChatServerObj.ClientHandler.DOWNLOAD)) {
                            if (!checkFiles(msg.getContent(), false)) {
                                System.out.println("Permitted by client.");
                                continue;
                            }
                        }
                        if (msg.getCommand().equalsIgnoreCase(ChatServerObj.ClientHandler.UPLOAD)) {
                            if (!checkFiles(msg.getContent(), true)) {
                                System.out.println("Permitted by client.");
                                continue;
                            }
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
        }
    }

    private static class FileConnection extends Thread {
        private final String filename;
        private final boolean ioFlag;
        private final Socket fileSocket;
        private final OutputStream outStream;
        private final InputStream inStream;


        public FileConnection(Socket socket, FileServerEntry fsEntry) throws IOException {
            fileSocket = socket;

            ObjectOutputStream outFile = new ObjectOutputStream(fileSocket.getOutputStream());
            outFile.writeObject(fsEntry);
            filename = fsEntry.getFilename();
            ioFlag = fsEntry.checkIOFlag();

            outStream = fileSocket.getOutputStream();
            inStream = fileSocket.getInputStream();

            start();
        }

        @Override
        public void run() {
            try {
                try {
//                    HERE YOU STOPPED YOUR RESEARCHES
                    initializeTransfer();
                    inStream.close();
                    outStream.close();
                    fileSocket.close();
                } finally {
                    inStream.close();
                    outStream.close();
                    fileSocket.close();
                }
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
            System.out.println("File thread died!");
        }

        private void initializeTransfer() throws IOException, ClassNotFoundException {
            File dir = new File(sourceDir);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new FileNotFoundException("Source dir is missing.");
                }
            }
            File file = new File(sourceDir + "\\" + filename);
            if (ioFlag && file.exists()) {
                upload(new FileInputStream(file));
            } else if (!ioFlag){
                download(new FileOutputStream(file));
            } else {
                throw new FileNotFoundException("Wrong filename or something like that.");
            }
        }

        private void download(OutputStream outFile) throws IOException, ClassNotFoundException {
            System.out.println("For us download started.");

            int count;
            byte[] buffer = new byte[8192]; // or 4096, or more
            while ((count = inStream.read(buffer)) > 0)
            {
                outFile.write(buffer, 0, count);
            }

            System.out.println("For us download ended.");
            outFile.close();
        }

        private void upload(InputStream inFile) throws IOException {
            System.out.println("For us upload started.");

            int count;
            byte[] buffer = new byte[8192]; // or 4096, or more
            while ((count = inFile.read(buffer)) > 0)
            {
                outStream.write(buffer, 0, count);
            }

            System.out.println("For us upload ended.");
            inFile.close();
        }
    }


    static public boolean checkFiles(String name, boolean flag) throws FileNotFoundException {
        File dir = new File(sourceDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new FileNotFoundException("Source dir is missing.");
            }
        }
        File file = new File(sourceDir + "\\" + name);
        if (flag && !file.exists()) {
            return false;
        } else if (!flag && file.exists()) {
            return false;
        } else {
            return true;
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
                fromServer.join();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                client.close();
                in.close();
                out.close();
            }
        } catch (Exception e) {}
    }
}