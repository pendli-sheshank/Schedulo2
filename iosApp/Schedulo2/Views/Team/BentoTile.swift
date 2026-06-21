import SwiftUI

struct BentoTile: View {
    let title: String
    let subtitle: String
    let systemImage: String
    let tint: Color
    var progress: Double? = nil
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 0) {
                HStack {
                    ZStack {
                        RoundedRectangle(cornerRadius: 10)
                            .fill(tint.opacity(0.18))
                            .frame(width: 36, height: 36)
                        Image(systemName: systemImage)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(tint)
                    }
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(tint.opacity(0.6))
                }
                Spacer()
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)
                    Text(subtitle)
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                    if let progress = progress {
                        ProgressView(value: progress)
                            .tint(tint)
                            .padding(.top, 4)
                    }
                }
            }
            .padding(16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background(
                RoundedRectangle(cornerRadius: 18)
                    .fill(tint.opacity(0.1))
            )
        }
        .buttonStyle(.plain)
    }
}
