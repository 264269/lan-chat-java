import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Paths;
import java.util.LinkedList;

public class FileServer extends Thread {
    public static final String sourceDir = Paths.get("").toAbsolutePath().toString() + "\\sources";

    private final int fileServerPort;

    private static LinkedList<FileHandler> fileSocketList = new LinkedList<>();


    static class FileHandler extends Thread {
        private final String filename;
        private final boolean ioFlag;
        private final Socket fileSocket;
        private final OutputStream outStream;
        private final InputStream inStream;

        FileHandler(Socket socket) throws IOException, ClassNotFoundException {
            fileSocket = socket;

            ObjectInputStream inFile = new ObjectInputStream(fileSocket.getInputStream());
            FileServerEntry fsEntry = (FileServerEntry) inFile.readObject();
            filename = fsEntry.getFilename();
            ioFlag = !fsEntry.checkIOFlag();

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
                } catch (IOException | ClassNotFoundException e) {
                    e.printStackTrace();
                } finally {
                    inStream.close();
                    outStream.close();
                    fileSocket.close();
                }
            } catch (IOException e) {
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
//            System.out.println("Transfer params:\n" +
//                    ioFlag + "\n" +
//                    filename + "\n");
            if (ioFlag && file.exists()) {
                upload(new FileInputStream(file));
            } else if (!ioFlag){
                download(new FileOutputStream(file));
            } else {
                throw new FileNotFoundException("Wrong filename or something like that.");
            }
        }

        private void download(OutputStream outFile) throws IOException, ClassNotFoundException {
            System.out.println("For " + this.fileSocket.getPort() + " download started.");

            int count;
            byte[] buffer = new byte[8192]; // or 4096, or more
            while ((count = inStream.read(buffer)) > 0)
            {
                outFile.write(buffer, 0, count);
            }

            System.out.println("For " + this.fileSocket.getPort() + " download ended.");
            outFile.close();
        }

        private void upload(InputStream inFile) throws IOException {
            System.out.println("For " + this.fileSocket.getPort() + " upload started.");

            int count;
            byte[] buffer = new byte[8192]; // or 4096, or more
            while ((count = inFile.read(buffer)) > 0)
            {
                outStream.write(buffer, 0, count);
            }


            System.out.println("For " + this.fileSocket.getPort() + " upload ended.");
            inFile.close();
        }

        public boolean isClosed() {
            return this.fileSocket.isClosed();
        }
    }

    public FileServer (int port) {
        fileServerPort = port;
        start();
    }
    @Override
    public void run() {
        try (ServerSocket fileServerSocket = new ServerSocket(fileServerPort, 4)) {
            while (true) {
                clearFileServer();
                System.out.println("File server is open for a new connection.");
                Socket fileSocket = fileServerSocket.accept();
                System.out.println("File server accepted connection.");
                try {
                    System.out.println("Trying to launch a file thread.");
                    fileSocketList.add(new FileHandler(fileSocket));
                    System.out.println("File server is ready to work with new client.");
                } catch (IOException e) {
                    System.out.println("Something went wrong, closing connection.");
                    fileSocket.close();
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void clearFileServer() {
        fileSocketList.removeIf(FileHandler::isClosed);
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
    static public String getFileList() throws FileNotFoundException{
        StringBuilder fileList = new StringBuilder();
        File dir = new File(sourceDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                throw new FileNotFoundException("Source dir is missing.");
            }
        }
        if (!dir.isDirectory()) {
            throw new FileNotFoundException("Source dir is missing.");
        }
        fileList.append("Files on server (").append(dir.listFiles().length).append("):");
        for (File file : dir.listFiles()) {
            fileList.append("\n");
            fileList.append(file.getName());
        }
        return fileList.toString();
    }
}
