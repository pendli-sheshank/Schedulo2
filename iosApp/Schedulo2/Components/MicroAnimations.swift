import SwiftUI

struct ScaleButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
            .animation(.spring(response: 0.3, dampingFraction: 0.7), value: configuration.isPressed)
    }
}

struct SuccessCheckmark: View {
    @State private var scale: CGFloat = 0
    @State private var opacity: Double = 0

    var body: some View {
        VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(Color.primaryGreen)
                    .frame(width: 64, height: 64)

                Image(systemName: "checkmark")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.white)
            }
            .scaleEffect(scale)
            .opacity(opacity)

            Text("Success!")
                .font(.system(size: 16, weight: .semibold))
                .foregroundColor(.primaryGreen)
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                scale = 1.0
                opacity = 1.0
            }
        }
    }
}

struct ShakeModifier: ViewModifier {
    @State private var offset: CGFloat = 0
    var trigger: Bool

    func body(content: Content) -> some View {
        content
            .offset(x: offset)
            .onChange(of: trigger) { _ in
                withAnimation(.easeInOut(duration: 0.1)) {
                    offset = -10
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
                    withAnimation(.easeInOut(duration: 0.1)) {
                        offset = 10
                    }
                }
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
                    withAnimation(.easeInOut(duration: 0.1)) {
                        offset = 0
                    }
                }
            }
    }
}

extension View {
    func scaleButtonStyle() -> some View {
        self.buttonStyle(ScaleButtonStyle())
    }

    func shake(trigger: Bool) -> some View {
        self.modifier(ShakeModifier(trigger: trigger))
    }

    func springScale() -> some View {
        self
            .transition(.scale.combined(with: .opacity))
            .animation(.spring(response: 0.4, dampingFraction: 0.7), value: UUID())
    }

    func slideInFromRight() -> some View {
        self
            .transition(.move(edge: .trailing).combined(with: .opacity))
            .animation(.spring(response: 0.5, dampingFraction: 0.8), value: UUID())
    }

    func slideOutToRight() -> some View {
        self
            .transition(.move(edge: .trailing).combined(with: .opacity))
            .animation(.easeInOut(duration: 0.3), value: UUID())
    }
}

#Preview {
    VStack(spacing: 24) {
        SuccessCheckmark()
        Spacer()
    }
}
