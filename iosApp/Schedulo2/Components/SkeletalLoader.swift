import SwiftUI

struct SkeletalLoader: View {
    @State private var isAnimating = false

    var body: some View {
        VStack(spacing: 16) {
            skeletalCard()
            skeletalCard()
            skeletalCard()
        }
        .padding(.horizontal, 16)
        .onAppear {
            withAnimation(.easeInOut(duration: 1.5).repeatForever(autoreverses: true)) {
                isAnimating = true
            }
        }
    }

    private func skeletalCard() -> some View {
        VStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 12)
                .fill(Color(UIColor.secondarySystemBackground))
                .frame(height: 20)
                .shimmer(isAnimating: isAnimating)

            HStack(spacing: 8) {
                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(UIColor.secondarySystemBackground))
                    .frame(width: 60, height: 16)
                    .shimmer(isAnimating: isAnimating)

                Spacer()

                RoundedRectangle(cornerRadius: 8)
                    .fill(Color(UIColor.secondarySystemBackground))
                    .frame(width: 80, height: 16)
                    .shimmer(isAnimating: isAnimating)
            }

            RoundedRectangle(cornerRadius: 8)
                .fill(Color(UIColor.secondarySystemBackground))
                .frame(height: 12)
                .shimmer(isAnimating: isAnimating)
        }
        .padding(16)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color(UIColor.systemBackground))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(UIColor.separator).opacity(0.3), lineWidth: 1)
                )
        )
    }
}

extension View {
    func shimmer(isAnimating: Bool) -> some View {
        self
            .overlay(
                LinearGradient(
                    gradient: Gradient(stops: [
                        .init(color: .white.opacity(0), location: 0),
                        .init(color: .white.opacity(0.1), location: 0.5),
                        .init(color: .white.opacity(0), location: 1)
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
                .offset(x: isAnimating ? 1000 : -1000)
            )
            .clipped()
    }
}

#Preview {
    SkeletalLoader()
}
