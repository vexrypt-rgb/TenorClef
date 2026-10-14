package adris.altoclef.eventbus.events;

/**
 * A line the server put in chat itself (not a player speaking, not the action bar).
 */
public class GameMessageEvent {
    public String message;

    public GameMessageEvent(String message) {
        this.message = message;
    }
}
