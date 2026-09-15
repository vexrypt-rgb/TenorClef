package adris.altoclef.tasks.speedrun.testrun2.move;

/** Single access point. Swap implementation here when the fork is ready. */
public final class T2Movers {

    private static T2Mover inst;

    private T2Movers() {}

    public static T2Mover get() {
        if (inst == null) inst = new BaritoneMover();
        return inst;
    }

    public static void use(T2Mover mover) {
        inst = mover;
    }
}
