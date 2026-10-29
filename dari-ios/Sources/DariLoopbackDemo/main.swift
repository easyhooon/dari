import Dari
import Foundation

// Records a few bridge calls and keeps serving them until killed. Pass `--tick` to add one call per second.
let dari = Dari.shared
try dari.start()
dari.setEnvironment(["server": "demo"])
let bridge = dari.interceptor(tag: "demo")
bridge.onPageUrlChanged("https://example.com/checkout?session=1#top")

bridge.onWebToAppRequest(handlerName: "getUser", requestId: "1", requestData: #"{"accessToken":"secret-value","id":7}"#)
bridge.onWebToAppResponse(handlerName: "getUser", requestId: "1", responseData: #"{"name":"Dari"}"#, isSuccess: true)
bridge.onAppToWebRequest(handlerName: "onResume", requestId: "1", data: "{}")
bridge.onAppToWebResponse(requestId: "1", isSuccess: false, responseData: #"{"error":"no listener"}"#)
bridge.onWebToAppRequest(handlerName: "openCamera", displayName: "Open camera", requestId: "2", requestData: nil)
bridge.onWebToAppRequest(handlerName: "log", requestId: nil, requestData: "fire and forget", fireAndForget: true)

setvbuf(stdout, nil, _IONBF, 0)
print("DARI_PORT=\(dari.port ?? 0)")

if CommandLine.arguments.contains("--tick") {
    var count = 0
    Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { _ in
        count += 1
        bridge.onWebToAppRequest(handlerName: "tick", requestId: "t\(count)", requestData: #"{"count":\#(count)}"#)
    }
}
RunLoop.main.run()
