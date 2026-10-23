package fixtures;

public class NamedBridge {
    public void onRequest(String handlerName, long size, double ratio, String requestId) {
    }

    public void onRequest(String requestId) {
    }

    public static void send(long size, double ratio, String requestId) {
    }
}
