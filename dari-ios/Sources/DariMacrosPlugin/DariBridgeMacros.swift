import SwiftCompilerPlugin
import SwiftSyntax
import SwiftSyntaxMacros

@main
struct DariMacrosPlugin: CompilerPlugin {
    let providingMacros: [Macro.Type] = [DariBridgeMacro.self, DariBridgeHandlerMacro.self]
}

/// Adds `@DariBridgeHandler` with the same arguments to each message handler method.
public struct DariBridgeMacro: MemberAttributeMacro {
    public static func expansion(
        of node: AttributeSyntax,
        attachedTo declaration: some DeclGroupSyntax,
        providingAttributesFor member: some DeclSyntaxProtocol,
        in context: some MacroExpansionContext
    ) throws -> [AttributeSyntax] {
        guard let function = member.as(FunctionDeclSyntax.self),
              (try? BridgeHandler(function)) != nil,
              !function.attributes.contains(where: { $0.as(AttributeSyntax.self)?.attributeName.trimmedDescription == "DariBridgeHandler" })
        else { return [] }
        var attribute = node.trimmed
        attribute.attributeName = TypeSyntax(IdentifierTypeSyntax(name: "DariBridgeHandler"))
        return [attribute]
    }
}

/// Records the message, and the reply when there is one, before the handler's own body runs.
public struct DariBridgeHandlerMacro: BodyMacro {
    public static func expansion(
        of node: AttributeSyntax,
        providingBodyFor declaration: some DeclSyntaxProtocol & WithOptionalCodeBlockSyntax,
        in context: some MacroExpansionContext
    ) throws -> [CodeBlockItemSyntax] {
        guard let function = declaration.as(FunctionDeclSyntax.self), let handler = try BridgeHandler(function) else {
            throw BridgeError("@DariBridgeHandler goes on userContentController(_:didReceive:) or userContentController(_:didReceive:replyHandler:)")
        }
        guard let body = function.body else { return [] }
        let arguments = node.arguments?.as(LabeledExprListSyntax.self).map { ", \($0.trimmedDescription)" } ?? ""
        let record: CodeBlockItemSyntax = if let reply = handler.reply {
            "let \(raw: reply) = _DariBridgeSupport.record(\(raw: handler.message)\(raw: arguments), replyHandler: \(raw: reply))"
        } else {
            "_DariBridgeSupport.record(\(raw: handler.message)\(raw: arguments))"
        }
        return [record] + Array(body.statements)
    }
}

/// The parameter names of a message handler method, or nil when [function] isn't one.
struct BridgeHandler {
    let message: String
    let reply: String?

    init?(_ function: FunctionDeclSyntax) throws {
        let parameters = Array(function.signature.parameterClause.parameters)
        guard function.name.text == "userContentController",
              parameters.count == 2 || parameters.count == 3,
              parameters[1].firstName.text == "didReceive",
              parameters.count == 2 || parameters[2].firstName.text == "replyHandler"
        else { return nil }
        if function.signature.effectSpecifiers != nil {
            throw BridgeError("async message handlers aren't supported yet; use the replyHandler variant")
        }
        message = try Self.name(parameters[1])
        reply = parameters.count == 3 ? try Self.name(parameters[2]) : nil
    }

    private static func name(_ parameter: FunctionParameterSyntax) throws -> String {
        let name = (parameter.secondName ?? parameter.firstName).text
        if name == "_" { throw BridgeError("name the \(parameter.firstName.text) parameter so @DariBridgeHandler can record it") }
        return name
    }
}

struct BridgeError: Error, CustomStringConvertible {
    let description: String
    init(_ description: String) { self.description = description }
}
