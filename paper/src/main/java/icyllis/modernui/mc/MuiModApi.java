package icyllis.modernui.mc;

public final class MuiModApi {
    private MuiModApi() {
    }

    public static void postToUiThread(Runnable runnable) {
        runnable.run();
    }
}
