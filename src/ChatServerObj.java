import java.io.*;
import java.net.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;


public class ChatServerObj {
    static NetworkInterface networkInterface = null;

    //    client list
    private static LinkedList<ClientHandler> clientList = new LinkedList<>();


    static class ClientHandler extends Thread {
        //        some fields
        private final Socket client;
        private final ObjectInputStream in;
        private final ObjectOutputStream out;
        private String username = null;

        //        some Stringy things


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
                    Message encounter = Message.serverMessage("Type /commands.");
                    encounter.setCommand(Message.COMMANDS);
                    sendMsgToClient(encounter);
                    while (!client.isClosed()) {
                        msg = (Message) in.readObject();
                        msg.setFromUser(this.username);
                        try {
                            switch (msg.getCommand().toLowerCase(Locale.ROOT)) {
                                case (Message.REGISTRATION) -> {
                                    if (this.isRegistered()) {
                                        Message result = Message.serverMessage("You are already registered.");
                                        result.setToUser(this.getUsername());
                                        result.setCommand(Message.REGISTRATION);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    String username = msg.getContent().replaceAll("\\s*", "");
                                    if (registerUser(username)) {
                                        Message result = Message.serverMessage("Registration complete! Your username: " + username);
                                        result.setToUser(this.getUsername());
                                        this.sendMsgToClient(result);
                                    } else {
                                        Message result = Message.serverMessage("Error! Please, try again.");
                                        result.setCommand(Message.REGISTRATION);
                                        this.sendMsgToClient(result);
                                    }
                                }
                                case (Message.GROUP) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    groupChat(msg);
                                }
                                case (Message.WHISPER) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setToUser(this.username);
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (msg.getToUser().equalsIgnoreCase(this.username)) {
                                        Message result = Message.serverMessage("Stop it.");
                                        result.setToUser(this.username);
                                        result.setCommand(Message.REFUSED);
                                        sendMsgToClient(result);
                                        break;
                                    }
                                    ClientHandler toClient = getByUsername(msg.getToUser());
                                    if (toClient == null) {
                                        Message result = Message.serverMessage("Can't find such user.");
                                        result.setToUser(this.username);
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    toClient.sendMsgToClient(msg);
                                    sendMsgToClient(msg);
                                }
                                case (Message.QUIT) -> {
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
                                case (Message.USERS) -> {
                                    Message result = Message.serverMessage(getAllUsernames());
                                    result.setToUser(this.username);
                                    result.setCommand(Message.USERS);
                                    this.sendMsgToClient(result);
                                }
                                case (Message.FILES) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    Message result = Message.serverMessage(FileServer.getFileList());
                                    result.setToUser(this.username);
                                    result.setCommand(Message.FILES);
                                    sendMsgToClient(result);
                                }
                                case (Message.DOWNLOAD) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (FileServer.checkFiles(msg.getContent(), true)) {
                                        Message result = Message.serverMessage(msg.getContent());
                                        result.setCommand(Message.DOWNLOAD);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    } else {
                                        Message result = Message.serverMessage(null);
                                        result.setCommand(Message.REFUSED);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    }
                                }
                                case (Message.UPLOAD) -> {
                                    if (!this.isRegistered()) {
                                        Message result = Message.serverMessage("Permitted! You are not registered.");
                                        result.setCommand(Message.REFUSED);
                                        this.sendMsgToClient(result);
                                        break;
                                    }
                                    if (FileServer.checkFiles(msg.getContent(), false)) {
                                        Message result = Message.serverMessage(msg.getContent());
                                        result.setCommand(Message.UPLOAD);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    } else {
                                        Message result = Message.serverMessage(null);
                                        result.setCommand(Message.REFUSED);
                                        result.setToUser(this.username);
                                        result.setSystem(true);
                                        this.sendMsgToClient(result);
                                    }
                                }
                                case (Message.COMMANDS) -> this.sendMsgToClient(welcomeMsg());
                                default -> {
                                    throw new IllegalArgumentException("No such command.");
                                }
                            }
                        } catch (Exception e) {
                            Message result = Message.serverMessage("Error occurred on server - " + e.getMessage());
                            result.setCommand(Message.REFUSED);
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
            Message result = Message.serverMessage(welcome);
            result.setCommand(Message.COMMANDS);
            return result;
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
//            client.getInetAddress();
            return client.getPort();
        }
        public String getIP() {
            return client.getInetAddress().getHostAddress();
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


    private static class ClientAcceptance extends Thread {
        int serverPort;
        int filePort;
        boolean runFlag = true;
        FileServer fileServer;
        public ClientAcceptance(int port1, int port2) {
            serverPort = port1;
            filePort = port2;
            start();
        }
        @Override
        public void run() {
            fileServer = new FileServer(filePort);
            try (ServerSocket serverSocket = new ServerSocket(serverPort, 10)) {
                serverSocket.setSoTimeout(60000);
                while (runFlag) {
                    try {
                        clearServer();
                        System.out.println("Server is open for a new connection.");
                        Socket clientSocket = serverSocket.accept();
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
                        System.out.println(clientSocket.getInetAddress().getHostAddress());
                        Integer[] clientIP = getIP(clientSocket.getInetAddress().getAddress());
                        Integer[] prefix = getPrefix(networkInterface.getInterfaceAddresses().get(0).getNetworkPrefixLength());
                        Integer[] serverIP = getIP(networkInterface.getInterfaceAddresses().get(0).getAddress().getAddress());
                        boolean lanFlag = true;
                        for (int i = 0; i < 4; i++) {
                            if ((clientIP[i] & prefix[i]) != (serverIP[i] & prefix[i])) {
                                System.out.println("Connection from other LAN, closing connection.");
                                out.write(Message.REFUSED + "\n");
                                out.flush();
                                out.close();
                                clientSocket.close();
                                lanFlag = false;
                                break;
                            }
                        }
                        if (lanFlag) {
                            System.out.println("Connection accepted.");
                            out.write("yo\n");
                            out.flush();
                            try {
                                System.out.println("Trying to launch a thread.");
                                clientList.add(new ClientHandler(clientSocket));
                                System.out.println("Server is ready to work with new client.");
                            } catch (IOException e) {
                                System.out.println("Something went wrong, closing connection.");
                                clientSocket.close();
                            }
                        }
                    } catch (SocketTimeoutException e) {}
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        static Integer[] getPrefix(short prefix) {
            StringBuilder[] temp = new StringBuilder[4];
            for (int i = 0; i < 4; i++) {
                temp[i] = new StringBuilder();
            }
            for (int i = 0; i < 32; i++) {
                int index = i / 8;
                if (i < prefix) {
                    temp[index].append("1");
                } else {
                    temp[index].append("0");
                }
            }
            Integer[] result = new Integer[4];
            for (int i = 0; i < 4; i++) {
                result[i] = Integer.parseInt(temp[i].toString(), 2);
            }
            return result;
        }

        static Integer[] getIP(byte[] ip) {
            Integer[] result = new Integer[4];
            for (int i = 0; i < 4; i++) {
                if (ip[i] < 0)
                    result[i] = 256 + ip[i];
                else
                    result[i] = (int) ip[i];
            }
            return result;
        }

        public void exit() {
            runFlag = false;
            fileServer.exit();
        }
    }


    static void displayInterfaceInformation(NetworkInterface netint) throws SocketException {
        System.out.printf("Display name: %s\n", netint.getDisplayName());
        System.out.printf("Name: %s\n", netint.getName());
        Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
        for (InetAddress inetAddress : Collections.list(inetAddresses)) {
            if (inetAddress.getAddress().length == 4)
                System.out.println("InetAddress: " + inetAddress + "/" + netint.getInterfaceAddresses().get(0).getNetworkPrefixLength());
        }
        System.out.println();
    }

    public static void main(String[] args) throws IOException {
        ArrayList<NetworkInterface> nets = new ArrayList<>();
        for (NetworkInterface netint : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress inetAddress : Collections.list(netint.getInetAddresses())) {
                if (inetAddress.getAddress().length == 4) {
                    nets.add(netint);
                }
            }
        }
        System.out.println("You can currently host in " + nets.size() + " networks:\n");
        for (NetworkInterface netint : nets) {
            displayInterfaceInformation(netint);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Choose network to host (enter interface name):");
        String msg;
        while ((msg = br.readLine()) != null) {
            if (msg.equalsIgnoreCase(Message.QUIT)) {
                System.out.println("Bye!");
                return;
            }
            for (NetworkInterface netint : nets)
                if (netint.getName().equalsIgnoreCase(msg)) {
                    System.out.println("Network chosen successfully!");
                    networkInterface = netint;
                    break;
                }
            if (networkInterface != null)
                break;
            System.out.println("Can't find such interface!");
        }
        ClientAcceptance clientAcceptance = new ClientAcceptance(6666, 4004);
        String sysMsg;
        while (true) {
            BufferedReader sys = new BufferedReader(new InputStreamReader(System.in));
            sysMsg = sys.readLine();
            if (sysMsg.equalsIgnoreCase(Message.QUIT)) {
                System.out.println(sysMsg);
                break;
            }
        }
        clientAcceptance.exit();
    }
}
