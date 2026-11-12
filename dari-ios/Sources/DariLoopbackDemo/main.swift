import Dari
import Foundation

// Records a few bridge calls and keeps serving them until killed. Pass `--tick` to add one call per second.
let dari = Dari.shared
try dari.start()
dari.setEnvironment(["server": "demo"])
// Reads google.protobuf.StringValue without SwiftProtobuf: tag 0x0A, one-byte length, UTF-8.
let decoder = DariProtobufDecoder().webToApp("greet", request: { #"{"value":"\#(String(decoding: $0.dropFirst(2), as: UTF8.self))"}"# })
let bridge = dari.interceptor(tag: "demo", protobufDecoder: decoder)
bridge.onPageUrlChanged("https://example.com/checkout?session=1#top")

bridge.onWebToAppRequest(handlerName: "getUser", requestId: "1", requestData: #"{"accessToken":"secret-value","id":7}"#)
bridge.onWebToAppResponse(handlerName: "getUser", requestId: "1", responseData: #"{"name":"Dari"}"#, isSuccess: true)
bridge.onAppToWebRequest(handlerName: "onResume", requestId: "1", data: "{}")
bridge.onAppToWebResponse(requestId: "1", isSuccess: false, responseData: #"{"error":"no listener"}"#)
bridge.onWebToAppRequest(handlerName: "openCamera", displayName: "Open camera", requestId: "2", requestData: nil)
bridge.onWebToAppRequest(handlerName: "log", requestId: nil, requestData: "fire and forget", fireAndForget: true)
bridge.onWebToAppProtobufRequest(handlerName: "greet", requestId: "3", requestData: Data([0x0A, 0x02, 0x68, 0x69]))

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
