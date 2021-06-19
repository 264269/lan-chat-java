import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;


public class ChatServerObj {

    //    client list
    private static LinkedList<ClientHandler> clientList = new LinkedList<>();

    static class ClientHandler extends Thread {
        //        some fields
        private final Socket client;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        private String username = null;

        //        some Stringy things
        public static final String REGISTRATION = "/register";
        public static final String GROUP = "/g";
        public static final String WHISPER = "/w";
        public static final String QUIT = "/q";
        public static final String USERS = "/users";
        public static final String DOWNLOAD = "/download";
        public static final String UPLOAD = "/upload";
        public static final String FILES = "/files";
        public static final String REFUSED = "refused";


        //        constructor
        public ClientHandler(Socket socket) throws IOException{
            client = socket;
            in = new ObjectInputStream(client.getInputStream());
            out = new ObjectOutputStream(client.getOutputStream());
            start();
        }

        //        methods
        @Override
        public void run() {
            Message msg;
            try {
                try {
                    sendMsgToClient(welcomeMsg());
                    while (!client.isClosed()) {
                        msg = (Message) in.readObject();
                        msg.setFromUser(this.username);
                        try {
                            switch (msg.getCommand().toLowerCase(Locale.ROOT)) {
                                case (REGISTRATION) -> {
                                    if (this.isRegistered()) {
                                        Message result = Message.serverMessage("You are already registered.");
                                        result.setToUser(this.getUsername());
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    String username = msg.getContent().replaceAll("\\s*", "");
                                    if (registerUser(username)) {
                                        Message result = Message.serverMessage("Registration complete! Your username: " + username);
                                        result.setToUser(this.getUsername());
                                        this.sendMsgToClient(result);
                                    } else {
                                        this.sendMsgToClient(Message.serverMessage("Error! Please, try again."));
                                    }
                                }
                                case (GROUP) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    groupChat(msg);
                                }
                                case (WHISPER) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setToUser(this.username);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (msg.getToUser().equalsIgnoreCase(this.username)) {
                                        Message result = Message.serverMessage("Stop it.");
                                        result.setToUser(this.username);
                                        sendMsgToClient(result);
                                        break;
                                    }
                                    ClientHandler toClient = getByUsername(msg.getToUser());
                                    if (toClient == null) {
                                        Message result = Message.serverMessage("Can't find such user.");
                                        result.setToUser(this.username);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    toClient.sendMsgToClient(msg);
                                    sendMsgToClient(msg);
                                }
                                case (QUIT) -> {
                                    Message resultToUser = Message.serverMessage("You're going to be disconnected.");
                                    resultToUser.setToUser(this.username);
                                    this.sendMsgToClient(resultToUser);
                                    deleteClient(this.getPort());
                                    in.close();
                                    out.close();
                                    client.close();
                                    if (this.isRegistered()) {
                                        Message resultToChat = Message.serverMessage("User " + this.username + " is off-line.");
                                        groupChat(resultToChat);
                                    }
                                }
                                case (USERS) -> {
                                    Message result = Message.serverMessage(getAllUsernames());
                                    result.setToUser(this.username);
                                    this.sendMsgToClient(result);
                                }
                                case (FILES) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    Message result = Message.serverMessage(FileServer.getFileList());
                                    result.setToUser(this.username);
                                    sendMsgToClient(result);
                                }
                                case (DOWNLOAD) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (FileServer.checkFiles(msg.getContent(), true)) {
                                        Message result = Message.serverMessage(msg.getContent());
                                        result.setCommand(DOWNLOAD);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    } else {
                                        Message result = Message.serverMessage(null);
                                        result.setCommand(REFUSED);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    }
                                }
                                case (UPLOAD) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (FileServer.checkFiles(msg.getContent(), false)) {
                                        Message result = Message.serverMessage(msg.getContent());
                                        result.setCommand(UPLOAD);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    } else {
                                        Message result = Message.serverMessage(null);
                                        result.setCommand(REFUSED);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    }
                                }
                                default -> {
                                    throw new IllegalArgumentException("No such command.");

                                }
                            }
                        } catch (Exception e) {
                            Message result = Message.serverMessage("Error occurred on server - " + e.getMessage());
                            result.setToUser(this.username);
                            this.sendMsgToClient(result);
                        }
                    }
                } catch (IOException e) {
                    in.close();
                    out.close();
                    client.close();
                    if (this.isRegistered()) {
                        Message resultToChat = Message.serverMessage("User " + this.username + " is off-line.");
                        groupChat(resultToChat);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //        DONE methods
        public Message welcomeMsg(){
            String welcome = new String("""
                    Welcome! Register, please!
                    Commands are:
                    /register <username> - registration
                    /g <content> - group chat
                    /w <username> <content> - private msg
                    /users - check online users
                    /files - check available files
                    /upload <filename> - upload file
                    /download <filename> - download file
                    /q - quit chat
                    """);
            return Message.serverMessage(welcome);
        }

        public boolean isRegistered(){
            return (username != null);
        }

        public boolean isClosed(){
            return client.isClosed();
        }

        public String getUsername(){
            return username;
        }

        public int getPort() {
            return client.getPort();
        }

        public boolean registerUser(String name){
            if (name.equalsIgnoreCase("null")){
                return false;
            }
            if (Pattern.compile("^[0-9]*$", Pattern.CASE_INSENSITIVE).matcher(name).find()) {
                return false;
            }
            if (Pattern.compile("^server$", Pattern.CASE_INSENSITIVE).matcher(name).find()) {
                return false;
            }
            for (ClientHandler client : clientList){
                if (name.equalsIgnoreCase(client.getUsername())){
                    return false;
                }
            }
            username = name;
            return true;
        }
        private void groupChat(Message msg) {
            msg.setToUser("everyone on server");
            for (ClientHandler client : clientList) {
                try {
                    client.sendMsgToClient(msg);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public String getAllUsernames() {
            StringBuilder userList = new StringBuilder();
            userList.append("Users online: ").append(clientList.toArray().length);
            for (ClientHandler client : clientList) {
                userList.append("\n");
                userList.append(client.getUsername());
            }
            return userList.toString();
        }

        private void sendMsgToClient(Message msg) throws IOException {
            out.writeObject(msg);
            out.flush();
        }

        static public ClientHandler getByUsername(String name){
            for (ClientHandler client : clientList){
                if (name.equalsIgnoreCase(client.getUsername())){
                    return client;
                }
            }
            return null;
        }

    }

    private static void deleteClient(int deleteClientPort) {
        if (clientList.removeIf(client -> client.getPort() == deleteClientPort)) {
            System.out.println("Client with port " + deleteClientPort + " was deleted.");
        } else {
            System.out.println("Client wasn't deleted.");
        }
    }
    private static void clearServer(){
        clientList.removeIf(ClientHandler::isClosed);
    }

    public static void main(String[] args) throws IOException {
        int serverPort = 6666;
        FileServer fileServer = new FileServer(4004);
        try (ServerSocket serverSocket = new ServerSocket(serverPort, 10)) {
            while (true) {
                clearServer();
                System.out.println("Server is open for a new connection.");
                Socket clientSocket = serverSocket.accept();
                System.out.println("Connection accepted.");
                try {
                    System.out.println("Trying to launch a thread.");
                    clientList.add(new ClientHandler(clientSocket));
                    System.out.println("Server is ready to work with new client.");
                } catch (IOException e) {
                    System.out.println("Something went wrong, closing connection.");
                    clientSocket.close();
                }
            }
        }
    }
}
