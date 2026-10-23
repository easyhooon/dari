package fixtures;

import com.easyhooon.dari.interceptor.DariInterceptor;

public class ManualBridge {
    public DariInterceptor interceptor;

    public void onRequest(String handlerName) {
        if (interceptor != null) interceptor.onWebToAppRequest(handlerName);
    }
}
