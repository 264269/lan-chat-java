import java.io.Serializable;

public class FileServerEntry implements Serializable {
    private byte[] fileBytes;
    private boolean endFlag;
    private String filename;
    private boolean ioFlag;

    private FileServerEntry(String name, boolean flag){
        filename = name;
        ioFlag = flag;
    }
    private FileServerEntry() {}

    public boolean isEndFlag() {
        return endFlag;
    }
    public boolean checkIOFlag() {
        return ioFlag;
    }
    public String getFilename() {
        return filename;
    }
    public byte[] getFileBytes() {
        return fileBytes;
    }

    public void setEndFlag(boolean endFlag) {
        this.endFlag = endFlag;
    }
    public void setIoFlag(boolean ioFlag) {
        this.ioFlag = ioFlag;
    }
    public void setFilename(String filename) {
        this.filename = filename;
    }
    public void setFileBytes(byte[] fileBytes) {
        this.fileBytes = fileBytes;
    }

    static public FileServerEntry downloadRequest(String name) {
        return new FileServerEntry(name, false);
    }
    static public FileServerEntry uploadRequest(String name) {
        return new FileServerEntry(name, true);
    }
    static public FileServerEntry entryToSend(byte[] bytes) {
        FileServerEntry result = new FileServerEntry();
        result.setFileBytes(bytes);
        return result;
    }
    static public FileServerEntry entryToStop() {
        FileServerEntry result = new FileServerEntry();
        result.setEndFlag(true);
        return result;
    }
}
