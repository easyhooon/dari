import Foundation

/// Objective-C entry point over [Dari], for Kotlin/Native (`dari-kmp`) and Objective-C callers.
/// The runtime names are fixed so `NSClassFromString("DariObjC")` finds the class.
@objc(DariObjC)
public final class DariObjC: NSObject {
    /// Starts with the default configuration. Returns nil on success, or the error description.
    @objc public static func start() -> String? {
        do {
            try Dari.shared.start()
            return nil
        } catch {
            return String(describing: error)
        }
    }

    @objc public static func stop() {
        Dari.shared.stop()
    }

    @objc(interceptorWithTag:)
    public static func interceptor(tag: String?) -> DariObjCInterceptor {
        DariObjCInterceptor(Dari.shared.interceptor(tag: tag))
    }
}

/// Forwards to a [DariInterceptor]. `fireAndForget` is an `NSNumber` because `Bool?` isn't representable in Objective-C.
@objc(DariObjCInterceptor)
public final class DariObjCInterceptor: NSObject {
    private let interceptor: DariInterceptor

    init(_ interceptor: DariInterceptor) {
        self.interceptor = interceptor
    }

    @objc public var tag: String? { interceptor.tag }

    @objc(onPageUrlChanged:)
    public func onPageUrlChanged(_ url: String?) {
        interceptor.onPageUrlChanged(url)
    }

    @objc(onWebToAppRequestWithHandlerName:displayName:requestId:requestData:fireAndForget:)
    public func onWebToAppRequest(handlerName: String, displayName: String?, requestId: String?, requestData: String?, fireAndForget: NSNumber?) {
        interceptor.onWebToAppRequest(
            handlerName: handlerName,
            displayName: displayName,
            requestId: requestId,
            requestData: requestData,
            fireAndForget: fireAndForget?.boolValue
        )
    }

    @objc(onWebToAppResponseWithHandlerName:requestId:responseData:isSuccess:)
    public func onWebToAppResponse(handlerName: String, requestId: String?, responseData: String?, isSuccess: Bool) {
        interceptor.onWebToAppResponse(handlerName: handlerName, requestId: requestId, responseData: responseData, isSuccess: isSuccess)
    }

    @objc(onAppToWebRequestWithHandlerName:displayName:requestId:data:fireAndForget:)
    public func onAppToWebRequest(handlerName: String, displayName: String?, requestId: String?, data: String?, fireAndForget: NSNumber?) {
        interceptor.onAppToWebRequest(
            handlerName: handlerName,
            displayName: displayName,
            requestId: requestId,
            data: data,
            fireAndForget: fireAndForget?.boolValue
        )
    }

    @objc(onAppToWebResponseWithRequestId:isSuccess:responseData:)
    public func onAppToWebResponse(requestId: String?, isSuccess: Bool, responseData: String?) {
        interceptor.onAppToWebResponse(requestId: requestId, isSuccess: isSuccess, responseData: responseData)
    }
}
