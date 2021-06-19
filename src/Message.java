import java.io.Serializable;
import java.util.regex.Pattern;

public class Message implements Serializable {
    private static final String SYSTEM = "Server";
    private boolean system;
    private String command;
    private String content;
    private String fromUser;
    private String toUser;

    private Message() {};

    public boolean isSystem() {
        return system;
    }
    public String getCommand() {
        return command;
    }
    public String getContent() {
        return content;
    }
    public String getFromUser() {
        return fromUser;
    }
    public String getToUser() {
        return toUser;
    }

    public void setCommand(String command) {
        this.command = command;
    }
    public void setContent(String content) {
        this.content = content;
    }
    public void setFromUser(String fromUser) {
        this.fromUser = fromUser;
    }
    public void setSystem(boolean system) {
        this.system = system;
    }
    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

    public static Message userMessage(String msg) throws IllegalArgumentException {
        if (Pattern.compile("^\\\\").matcher(msg).find() || Pattern.compile("\\\\$").matcher(msg).find()) {
            throw new IllegalArgumentException("Please, don't use backslash at the start or the end of your message.");
        }
        Message result = new Message();
        msg = msg.replaceFirst("\s*", "");
        result.setCommand(msg.split(" ")[0]);
        result.setContent(msg.replaceFirst(result.command, "").replaceFirst("\s*", ""));
        result.setSystem(false);
        return result;
    }
    public static Message serverMessage(String msg) {
        Message result = new Message();
        result.setContent(msg);
        result.setSystem(true);
        return result;
    }
}
