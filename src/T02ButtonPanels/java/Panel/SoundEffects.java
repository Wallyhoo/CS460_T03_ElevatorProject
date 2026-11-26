package T02ButtonPanels.java.Panel;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.net.URL;

/**
 * Implements the sound effects needed for the buzzer and ding function
 * @author Amber Smith
 */
public final class SoundEffects {
    private static final String DING_PATH = "/ding.mp3";
    private static final String BUZZER_PATH = "/buzzer.wav";
    private static MediaPlayer buzzerPlayer;

    private SoundEffects() {}

    // the method to make the ding sound play on javafx
    public static void playDing() {
        runOnFxThread(() -> {
            MediaPlayer player = createPlayer(DING_PATH);
            if (player == null) return;

            player.setOnEndOfMedia(() -> dispose(player));
            player.setOnError(() -> dispose(player));
            player.play();
        });
    }

    // play the buzzer sound effect on javafx
    public static void startBuzzer() {
        runOnFxThread(() -> {
            clearBuzzer();
            MediaPlayer player = createPlayer(BUZZER_PATH);
            if (player == null) return;

            buzzerPlayer = player;
            player.setCycleCount(MediaPlayer.INDEFINITE);
            player.setOnError(() -> {
                if (buzzerPlayer == player) buzzerPlayer = null;
                dispose(player);
            });
            player.play();
        });
    }

    // stop the buzzer sound
    public static void stopBuzzer() { runOnFxThread(SoundEffects::clearBuzzer); }

    // creates the media player object that plays the sound on javafx
    private static MediaPlayer createPlayer(String soundPath) {
        URL resource = SoundEffects.class.getResource(soundPath);
        if (resource == null) return null;

        try {
            return new MediaPlayer(new Media(resource.toExternalForm()));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    // private method that performs the actual buzzer stop and clear
    private static void clearBuzzer() {
        if (buzzerPlayer != null) {
            buzzerPlayer.stop();
            buzzerPlayer.dispose();
            buzzerPlayer = null;
        }
    }

    // stop the media player and dispose of it
    private static void dispose(MediaPlayer player) {
        player.stop();
        player.dispose();
    }

    // run the task on a javafx thread
    private static void runOnFxThread(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            task.run();
            return;
        }

        try {
            Platform.runLater(task);
        } catch (IllegalStateException ignored) { }
    }
}
