import java.io.Serializable;
import java.util.Locale;
import java.util.regex.Pattern;

public class Message implements Serializable {
    public static final String REGISTRATION = "/register";
    public static final String GROUP = "/g";
    public static final String WHISPER = "/w";
    public static final String QUIT = "/q";
    public static final String USERS = "/users";
    public static final String DOWNLOAD = "/download";
    public static final String UPLOAD = "/upload";
    public static final String FILES = "/files";
    public static final String REFUSED = "refused";
    public static final String COMMANDS = "/commands";
//    fields
    private boolean system;
    private String command;
    private String content;
    private String fromUser;
    private String toUser;

//    constructor
    private Message() {};

//    gets
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

//    sets
    public void setSystem(boolean system) {
        this.system = system;
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
    public void setToUser(String toUser) {
        this.toUser = toUser;
    }

//    method to create message on client
    public static Message userMessage(String msg) throws IllegalArgumentException {

        if (Pattern.compile("^\\\\").matcher(msg).find() || Pattern.compile("\\\\$").matcher(msg).find()) {
            throw new IllegalArgumentException("Please, don't use backslash at the start or the end of your message.");
        }

        Message result = new Message();
        msg = msg.replaceFirst("\s*", "");
        result.setCommand(msg.split("\s+")[0]);
        result.setContent(msg.replaceFirst(result.command, "").replaceFirst("\s+", ""));

        switch (result.getCommand().toLowerCase(Locale.ROOT)) {

            case (REGISTRATION) -> {
                if (result.getContent().isBlank()) {
                    throw new IllegalArgumentException("Stop it.");
                }
            }

            case (GROUP), (UPLOAD), (DOWNLOAD) -> {
                if (result.getContent() == null || result.getContent().isBlank() || result.getContent().isEmpty()) {
                    throw new IllegalArgumentException("Permitted! No content.");
                }
            }

            case (WHISPER) -> {
                if (result.getContent().split("\s+").length < 2 || result.getContent().isBlank() || result.getContent().isEmpty()) {
                    throw new IllegalArgumentException("Permitted! Missing username/content.");
                }
                result.setToUser(result.content.split(" ")[0]);
                result.setContent(result.content.replaceFirst(result.toUser, "").replaceFirst("\s+", ""));
            }

            case (QUIT), (USERS), (FILES), (COMMANDS) -> {}

            default -> throw new IllegalArgumentException("There's no such command!");
        }
        return result;
    }

//    method to create message on server
    public static Message serverMessage(String msg) {
        Message result = new Message();
        result.setFromUser("From server");
        result.setContent(msg);
        return result;
    }
}
