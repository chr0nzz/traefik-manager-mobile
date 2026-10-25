import SwiftUI

/// Full record for one access log line. Port of `LogDetailPane`.
struct LogDetailView: View {
    let line: LogLine
    let country: String?
    @State private var message: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: Spacing.md) {
                if let entry = line.entry {
                    header(entry)
                    DetailSection(title: String(localized: "Request"), systemImage: "doc.text.magnifyingglass", tint: Palette.blue) {
                        rows(entry)
                    }
                } else {
                    Text("Unparsed line").font(.title3.weight(.semibold))
                }
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    SectionLabel(text: String(localized: "Raw line"))
                    Text(line.raw)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(Spacing.md)
                        .background(Palette.card, in: RoundedRectangle(cornerRadius: Radius.md))
                        .overlay(RoundedRectangle(cornerRadius: Radius.md).strokeBorder(Palette.border))
                }
            }
            .padding(Spacing.lg)
            .frame(maxWidth: 820)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.bg)
        .navigationTitle(line.entry?.path.ifEmpty("/") ?? String(localized: "Unparsed line"))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    UIPasteboard.general.string = line.raw
                    message = String(localized: "Copied")
                } label: {
                    Image(systemName: "doc.on.doc")
                }
                .accessibilityLabel("Copy raw line")
            }
        }
        .toast($message)
    }

    private func header(_ entry: LogEntry) -> some View {
        let color: Color = entry.status >= 500 ? Palette.red : entry.status >= 400 ? Palette.yellow : entry.status > 0 ? Palette.green : Palette.muted
        return HStack(alignment: .firstTextBaseline, spacing: Spacing.sm) {
            Text(entry.status == 0 ? "-" : String(entry.status)).font(.title.weight(.bold).monospaced()).foregroundStyle(color)
            Text(entry.statusName ?? "").font(.subheadline).foregroundStyle(color)
            Spacer()
            Text(entry.method).font(.subheadline.monospaced()).foregroundStyle(Palette.muted)
        }
    }

    @ViewBuilder
    private func rows(_ entry: LogEntry) -> some View {
        DetailRow(label: String(localized: "Path"), value: entry.path.ifEmpty("-"), mono: true)
        DetailRow(label: "IP", value: entry.ip.ifEmpty("-"), mono: true)
        if !entry.date.isEmpty { DetailRow(label: String(localized: "Date"), value: entry.date, mono: true) }
        if let country { DetailRow(label: String(localized: "Country"), value: "\(Countries.flag(country)) \(Countries.name(country))") }
        if !entry.domain.isEmpty { DetailRow(label: String(localized: "Domain"), value: entry.domain, mono: true) }
        if !entry.scheme.isEmpty { DetailRow(label: String(localized: "Scheme"), value: entry.scheme, mono: true) }
        if !entry.entryPoint.isEmpty { DetailRow(label: String(localized: "Entry point"), value: entry.entryPoint, mono: true) }
        if !entry.router.isEmpty { DetailRow(label: String(localized: "Router"), value: entry.router, mono: true) }
        if !entry.service.isEmpty { DetailRow(label: String(localized: "Service"), value: entry.service, mono: true) }
        if !entry.serviceUrl.isEmpty { DetailRow(label: String(localized: "Service URL"), value: entry.serviceUrl, mono: true) }
        if !entry.size.isEmpty && entry.size != "-" { DetailRow(label: String(localized: "Size"), value: entry.size, mono: true) }
        DetailRow(label: String(localized: "Duration"), value: entry.duration.ifEmpty(LogParser.formatMs(entry.durMs)), mono: true)
        if let origin = entry.origin {
            DetailRow(
                label: String(localized: "Origin status"),
                value: origin == 0 ? String(localized: "0 tunnel (Traefik answered \(entry.status) itself)") : String(origin),
                mono: true
            )
        }
        if entry.retries > 0 { DetailRow(label: String(localized: "Retries"), value: String(entry.retries), mono: true) }
        DetailRow(label: "TLS", value: entry.tls.ifEmpty("-"), mono: true, last: true)
    }
}
