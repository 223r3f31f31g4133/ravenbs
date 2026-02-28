package keystrokesmod.command.impl;

import keystrokesmod.helper.PingHelper;
import keystrokesmod.command.Command;

public class Ping extends Command {
    public Ping() {
        super("ping");
    }

    @Override
    public void onExecute(String[] args) {
        PingHelper.checkPing(true);
    }
}
