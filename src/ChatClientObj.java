import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.rmi.ServerException;
import java.sql.Time;
import java.util.Arrays;
import java.util.Calendar;
import java.util.regex.Pattern;

public class ChatClientObj {
    private static final String sourceDir = Paths.get("").toAbsolutePath().toString() + "\\files";
    private static final String historyDir = Paths.get("").toAbsolutePath().toString() + "\\history";
    private static final String historyFile = historyDir + "\\history.txt";
    private static Socket client;
    private static BufferedReader sysIn;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static volatile boolean runFlag = true;


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
                        String msgPrintable = "(" + getTime() + ") " + parseMessage(msg);
                        System.out.println(msgPrintable);
                        if (msg.getCommand() == null || msg.getCommand().equalsIgnoreCase(Message.WHISPER)
                        || msg.getCommand().equalsIgnoreCase(Message.GROUP)) {
                            try {
                                writeHistory(msgPrintable);
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                            }
                        }
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
            if (!msg.getCommand().equalsIgnoreCase(Message.REFUSED)) {
                try {
                    Socket fileSocket = new Socket("localhost", 4004);
                    FileServerEntry fsEntry;
                    if (msg.getCommand().equalsIgnoreCase(Message.UPLOAD)) {
                        fsEntry = FileServerEntry.uploadRequest(msg.getContent());
                    } else {
                        fsEntry = FileServerEntry.downloadRequest(msg.getContent());
                    }
                    //    private static boolean fileFlag = false;
                    FileConnection fileConnection = new FileConnection(fileSocket, fsEntry);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        private static void writeHistory(String msg) throws IOException {
            File dir = new File(historyDir);
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    throw new FileNotFoundException("History dir is missing. Your story won't be stored.");
                }
            }
            File file = new File(historyFile);
            BufferedWriter fromHistory = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file, true)));
            fromHistory.write(msg + "\n");
            fromHistory.flush();
            fromHistory.close();
        }
        private static String getTime() {
            StringBuilder time = new StringBuilder();
            time.append(java.time.ZonedDateTime.now().getDayOfMonth()).append(".").append(java.time.ZonedDateTime.now().getMonthValue())
                    .append(".").append(java.time.ZonedDateTime.now().getYear()).append(" ")
                    .append(java.time.ZonedDateTime.now().getHour()).append(":").append(java.time.ZonedDateTime.now().getMinute())
                    .append(":").append(java.time.ZonedDateTime.now().getSecond());
            return time.toString();
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
                        if (msg.getCommand().equalsIgnoreCase(Message.QUIT)) {
                            runFlag = false;
                        }
                        if (msg.getCommand().equalsIgnoreCase(Message.DOWNLOAD)) {
                            if (!checkFiles(msg.getContent(), false)) {
                                System.out.println("Permitted by client.");
                                continue;
                            }
                        }
                        if (msg.getCommand().equalsIgnoreCase(Message.UPLOAD)) {
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


    private static String parseMessage(Message msg) {
        return msg.getFromUser() + " -> " + msg.getToUser() + ": " + msg.getContent();
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

    static public void revealHistory() throws IOException {
        File dir = new File(historyDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new FileNotFoundException("History dir is missing. Can't get your story.");
            }
        }
        File file = new File(historyFile);
        if (!file.exists()) {
            FileOutputStream fileOut = new FileOutputStream(file);
            fileOut.write(new byte[0]);
            fileOut.flush();
            fileOut.close();
            return;
        }
        System.out.println("----\nYour chat history:");
        BufferedReader fromHistory = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
        String msg;
        while ((msg = fromHistory.readLine()) != null) {
            System.out.println(msg);
        }
        System.out.println("----");
        fromHistory.close();
    }

    public static void main(String[] args) {
        try {
            sysIn = new BufferedReader(new InputStreamReader(System.in));
            System.out.println("Enter server's ip to connect to chat (type \"/q\" to close client):");
            String msg;
            while ((msg = sysIn.readLine()) != null) {
                if (msg.equalsIgnoreCase(Message.QUIT)) {
                    System.out.println("Bye!");
                    return;
                }
                try {
                    System.out.println("Connecting to server.");
                    client = new Socket(msg,6666);
                    System.out.println("Connected.");
                    BufferedReader check =  new BufferedReader(new InputStreamReader(client.getInputStream()));
                    if (check.readLine().equalsIgnoreCase(Message.REFUSED)) {
                        check.close();
                        client.close();
                        client = null;
                        throw new SocketException();
                    }
                } catch (SocketException e) {
                    System.out.println("Can't connect to server.");
                }
                if (client != null)
                    break;
            }
            try {
                System.out.println("Connected successfully!");
                try {
                    revealHistory();
                } catch (IOException e) {
                    System.out.println(e.getMessage());
                }
                out = new ObjectOutputStream(client.getOutputStream());
                in = new ObjectInputStream(client.getInputStream());
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