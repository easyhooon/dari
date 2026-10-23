package fixtures;

public class Bridge {
    public String last;

    public void onRequest(String handlerName, String requestId, String data) {
        last = handlerName;
    }

    public static void send(String handlerName, boolean success, int code, long size) {
    }

    public int earlyReturn(String value) {
        if (value == null) return 0;
        return value.length();
    }
}
