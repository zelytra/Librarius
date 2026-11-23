package zelytra.librarius.push;

/**
 * One push notification (#69): what the system tray shows, and the in-app path tapping it opens.
 */
public record PushMessage(String title, String body, String path) {
}
