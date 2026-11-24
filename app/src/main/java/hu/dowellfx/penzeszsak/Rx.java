package hu.dowellfx.penzeszsak;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Rendszerindítás / frissítés után újraindítja a figyelést; az értesítés „Elutasít” gombja is ide jön. */
public class Rx extends BroadcastReceiver {
    static final String DECLINE = "hu.dowellfx.penzeszsak.DECLINE";

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (DECLINE.equals(a)) {
            Caller.stop(c);
            return;
        }
        if (Intent.ACTION_BOOT_COMPLETED.equals(a) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) {
            if (P.loggedIn(c)) ListenService.start(c);
        }
    }
}
