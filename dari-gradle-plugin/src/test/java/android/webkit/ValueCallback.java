package android.webkit;

// JVM stand-in for the platform interface.
public interface ValueCallback<T> {
    void onReceiveValue(T value);
}
