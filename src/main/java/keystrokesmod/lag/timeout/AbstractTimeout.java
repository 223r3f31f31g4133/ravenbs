package keystrokesmod.lag.timeout;

public abstract class AbstractTimeout {

    private boolean forcefullyTimedOut = false;

    protected abstract boolean shouldHaveTimedOut();

    public final boolean isTimedOut() {
        return forcefullyTimedOut || shouldHaveTimedOut();
    }

    public final void forceTimeOut() {
        forcefullyTimedOut = true;
    }

}