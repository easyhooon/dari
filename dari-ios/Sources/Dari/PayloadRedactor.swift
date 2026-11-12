import Foundation

/// A message as [Dari.setPayloadRedactor] sees it. Change the payloads or page URL; the rest identifies it.
public struct DariMessage: Equatable {
    public let id: Int64
    public let requestId: String?
    public let handlerName: String
    public let displayName: String?
    public let direction: DariMessageDirection
    public let tag: String?
    public var requestData: String?
    public var responseData: String?
    public var pageUrl: String?

    public init(
        id: Int64,
        requestId: String?,
        handlerName: String,
        displayName: String? = nil,
        direction: DariMessageDirection,
        tag: String? = nil,
        requestData: String? = nil,
        responseData: String? = nil,
        pageUrl: String? = nil
    ) {
        self.id = id
        self.requestId = requestId
        self.handlerName = handlerName
        self.displayName = displayName
        self.direction = direction
        self.tag = tag
        self.requestData = requestData
        self.responseData = responseData
        self.pageUrl = pageUrl
    }
}

public enum DariMessageDirection: Equatable {
    case webToApp
    case appToWeb
}

extension MessageRecord {
    static let redactorFailed = "[dari: payload hidden, redactor failed]"

    var message: DariMessage {
        DariMessage(
            id: id,
            requestId: requestId,
            handlerName: handlerName,
            displayName: displayName,
            direction: direction == .webToApp ? .webToApp : .appToWeb,
            tag: tag,
            requestData: requestData,
            responseData: responseData,
            pageUrl: pageUrl
        )
    }

    /// Fails closed like [PayloadMasker]: a throwing redactor hides every field it could edit.
    func redacted(by redactor: @Sendable (DariMessage) throws -> DariMessage) -> MessageRecord {
        var record = self
        do {
            let message = try redactor(message)
            record.requestData = message.requestData
            record.responseData = message.responseData
            record.pageUrl = message.pageUrl
        } catch {
            record.requestData = requestData.map { _ in Self.redactorFailed }
            record.responseData = responseData.map { _ in Self.redactorFailed }
            record.pageUrl = nil
        }
        return record
    }
}
