#if os(iOS)
import Dari
import SwiftUI
import UIKit

/// The in-app inspector: the recorded bridge messages, newest first, with search and share.
/// Present it with [Dari.presentInspector], or put it in your own `.sheet`.
public struct DariInspectorView: View {
    @StateObject private var model: InspectorModel
    @Environment(\.dismiss) private var dismiss
    @State private var export: ExportFile?
    @State private var confirmClear = false

    public init() {
        _model = StateObject(wrappedValue: InspectorModel())
    }

    init(model: InspectorModel) {
        _model = StateObject(wrappedValue: model)
    }

    public var body: some View {
        NavigationStack {
            List(model.visible, id: \.id) { record in
                NavigationLink(value: record.id) { MessageRow(record: record) }
            }
            .listStyle(.plain)
            .overlay { emptyState }
            .searchable(text: $model.query, prompt: "Handler, tag, payload")
            .navigationTitle("Dari")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(for: Int64.self) { id in MessageDetailView(model: model, id: id) }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItem(placement: .primaryAction) {
                    Menu {
                        Toggle(isOn: $model.maskingEnabled) {
                            Label("Mask Sensitive Values", systemImage: "eye.slash")
                        }
                        ForEach(InspectorFormat.ExportFormat.allCases) { format in
                            Button(format == .text ? "Export as Text" : "Export as JSON") {
                                let (records, masked) = (model.records, model.maskingEnabled)
                                Task { export = await ExportFile.make(format, newestFirst: records, masked: masked) }
                            }
                            .disabled(model.records.isEmpty)
                        }
                        Button("Clear", role: .destructive) { confirmClear = true }
                            .disabled(model.records.isEmpty)
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }

                }
            }
            .confirmationDialog("Clear all messages?", isPresented: $confirmClear, titleVisibility: .visible) {
                Button("Clear", role: .destructive) { model.clear() }
            } message: {
                Text("This also clears them in a connected desktop app.")
            }
            .sheet(item: $export) { ActivitySheet(items: [$0.item]) }
        }
        .onAppear { model.start() }
        .onDisappear { model.stop() }
    }

    @ViewBuilder private var emptyState: some View {
        if !model.isRunning {
            placeholder("Dari isn't running", "Call Dari.shared.start() in a debug build.")
        } else if model.records.isEmpty {
            placeholder("No messages yet", "Bridge calls appear here as they happen.")
        } else if model.visible.isEmpty {
            placeholder("No results", "Nothing matches \u{201C}\(model.query)\u{201D}.")
        }
    }

    private func placeholder(_ title: String, _ message: String) -> some View {
        VStack(spacing: 6) {
            Text(title).font(.headline)
            Text(message).font(.subheadline).foregroundStyle(.secondary)
        }
        .multilineTextAlignment(.center)
        .padding()
    }
}

struct MessageRow: View {
    let record: MessageRecord

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            StatusIcon(status: record.status).frame(width: 22)
            VStack(alignment: .leading, spacing: 3) {
                Text(record.displayName ?? record.handlerName)
                    .font(.body.weight(.semibold))
                    .lineLimit(1)
                HStack(spacing: 6) {
                    Text(InspectorFormat.direction(record.direction))
                    if let tag = record.tag { Text("· \(tag)") }
                }
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(1)
            }
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 3) {
                Text(Self.time(record.requestTimestamp)).font(.caption.monospacedDigit())
                Text(InspectorFormat.duration(record).map { "\($0) ms" } ?? InspectorFormat.size(InspectorFormat.requestSize(record)))
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.secondary)
            }
        }
        .accessibilityElement(children: .combine)
    }

    private static let timeFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss.SSS"
        return formatter
    }()

    static func time(_ millis: Int64) -> String {
        timeFormatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000))
    }
}

struct StatusIcon: View {
    let status: MessageStatus

    var body: some View {
        switch status {
        case .inProgress: Image(systemName: "clock").foregroundStyle(.orange).accessibilityLabel("In progress")
        case .success: Image(systemName: "checkmark.circle.fill").foregroundStyle(.green).accessibilityLabel("Success")
        case .error: Image(systemName: "xmark.octagon.fill").foregroundStyle(.red).accessibilityLabel("Error")
        }
    }
}

struct MessageDetailView: View {
    enum Tab: String, CaseIterable { case overview = "Overview", request = "Request", response = "Response" }

    @ObservedObject var model: InspectorModel
    let id: Int64
    @State private var tab = Tab.overview
    @State private var share: ExportFile?

    var body: some View {
        Group {
            if let raw = model.record(id: id) {
                let record = model.display(raw)
                VStack(spacing: 0) {
                    Picker("Section", selection: $tab) {
                        ForEach(Tab.allCases, id: \.self) { Text($0.rawValue) }
                    }
                    .pickerStyle(.segmented)
                    .padding([.horizontal, .bottom])
                    switch tab {
                    // Sizes come from the recorded payloads; masking only changes what's shown.
                    case .overview: OverviewList(record: record, recorded: raw)
                    case .request: PayloadView(data: record.requestData, truncated: record.requestDataTruncated, metadata: record.requestPayloadMetadata, masked: model.maskingEnabled)
                    case .response: PayloadView(data: record.responseData, truncated: record.responseDataTruncated, metadata: record.responsePayloadMetadata, masked: model.maskingEnabled)
                    }
                }
                .navigationTitle(record.displayName ?? record.handlerName)
                .toolbar {
                    // Formats on tap, not on every render: payloads can be hundreds of KB.
                    Button {
                        Task { share = await ExportFile.text(record) }
                    } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    .accessibilityLabel("Share")
                }
                .sheet(item: $share) { ActivitySheet(items: [$0.item]) }
            } else {
                Text("This message is no longer recorded.").foregroundStyle(.secondary)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct OverviewList: View {
    let record: MessageRecord
    let recorded: MessageRecord

    var body: some View {
        let formatter = InspectorFormat.defaultDateFormatter()
        func date(_ millis: Int64) -> String { formatter.string(from: Date(timeIntervalSince1970: Double(millis) / 1000)) }
        return List {
            Section {
                row("Handler", record.handlerName)
                if let name = record.displayName { row("Display name", name) }
                row("Direction", InspectorFormat.direction(record.direction))
                LabeledContent("Status") {
                    HStack(spacing: 4) { StatusIcon(status: record.status); Text(record.status.rawValue) }
                }
                row("Tag", record.tag ?? "-")
                if let page = record.pageUrl { row("Page", page) }
                row("Request ID", record.requestId ?? "-")
            }
            Section("Timing") {
                row("Request", date(record.requestTimestamp))
                if let response = record.responseTimestamp { row("Response", date(response)) }
                if let duration = InspectorFormat.duration(record) { row("Duration", "\(duration) ms") }
            }
            Section("Size") {
                row("Request", InspectorFormat.size(InspectorFormat.requestSize(recorded)) + (record.requestDataTruncated ? " (truncated)" : ""))
                row("Response", InspectorFormat.size(InspectorFormat.responseSize(recorded)) + (record.responseDataTruncated ? " (truncated)" : ""))
            }
            ForEach([("Request", record.requestPayloadMetadata), ("Response", record.responsePayloadMetadata)], id: \.0) { label, metadata in
                if let metadata {
                    Section("\(label) payload") {
                        row("Content type", "Protocol Buffers")
                        row("Decode status", metadata.decodeStatus.rawValue)
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }

    private func row(_ title: String, _ value: String) -> some View {
        LabeledContent(title) {
            Text(value).textSelection(.enabled).multilineTextAlignment(.trailing)
        }
    }
}

struct PayloadView: View {
    enum Mode: String, CaseIterable { case decoded = "Decoded", raw = "Raw" }

    let data: String?
    let truncated: Bool
    let metadata: PayloadMetadata?
    let masked: Bool
    @State private var mode = Mode.decoded
    @State private var rendered: String?

    var body: some View {
        VStack(spacing: 0) {
            if metadata?.rawPreview != nil {
                Picker("View", selection: $mode) {
                    ForEach(Mode.allCases, id: \.self) { Text($0.rawValue) }
                }
                .pickerStyle(.segmented)
                .padding([.horizontal, .bottom])
            }
            ScrollView([.vertical, .horizontal]) {
                Text(rendered ?? "")
                    .font(.system(.footnote, design: .monospaced))
                    .textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding()
            }
            .background(Color(uiColor: .secondarySystemBackground))
        }
        // Re-renders only when the payload or the view mode changes, off the main thread.
        .task(id: "\(mode.rawValue)|\(masked)|\(data?.utf8.count ?? -1)|\(metadata?.rawPreview?.sizeBytes ?? -1)") {
            let (data, truncated, metadata, mode) = (data, truncated, metadata, mode)
            rendered = await Task.detached(priority: .userInitiated) {
                Self.content(data: data, truncated: truncated, metadata: metadata, mode: mode)
            }.value
        }
    }

    static func content(data: String?, truncated: Bool, metadata: PayloadMetadata?, mode: Mode) -> String {
        if mode == .raw, let metadata, let preview = metadata.rawPreview {
            let captured = "Captured \(preview.sizeBytes) of \(metadata.originalSizeBytes) bytes\(preview.truncated ? " (truncated)" : "")"
            return "\(captured)\n\nHex\n\(InspectorFormat.hex(preview))\n\nBase64\n\(preview.base64)"
        }
        guard let data else { return "(empty)" }
        return InspectorFormat.prettyJSON(data) + (truncated ? "\n\n(truncated)" : "")
    }
}

/// Something to hand to the share sheet, built off the main thread.
struct ExportFile: Identifiable {
    let id = UUID()
    let item: Any

    static func text(_ record: MessageRecord) async -> ExportFile {
        let text = await Task.detached(priority: .userInitiated) { InspectorFormat.text(record) }.value
        return ExportFile(item: text)
    }

    /// Writes every message to a fresh folder, removing earlier exports first. Nil when writing fails.
    static func make(_ format: InspectorFormat.ExportFormat, newestFirst: [MessageRecord], masked: Bool) async -> ExportFile? {
        let app = Dari.shared.inspectorAppInfo
        return await Task.detached(priority: .userInitiated) { () -> ExportFile? in
            let root = FileManager.default.temporaryDirectory.appendingPathComponent("dari_export", isDirectory: true)
            try? FileManager.default.removeItem(at: root)
            let folder = root.appendingPathComponent(UUID().uuidString, isDirectory: true)
            let url = folder.appendingPathComponent(InspectorFormat.fileName(format))
            do {
                try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
                try InspectorFormat.export(newestFirst.reversed(), app: app, format: format, masked: masked).write(to: url, atomically: true, encoding: .utf8)
                return ExportFile(item: url)
            } catch {
                return nil
            }
        }.value
    }
}

struct ActivitySheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}
#endif
