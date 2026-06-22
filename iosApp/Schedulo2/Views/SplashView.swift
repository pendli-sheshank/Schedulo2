import SwiftUI

struct SplashView: View {
    @State private var logoScale: CGFloat = 0.5
    @State private var logoOpacity: Double = 0
    @State private var taglineOpacity: Double = 0
    @State private var sparkleOpacity: Double = 0
    @State private var sparkleRotation: Double = 0

    var body: some View {
        ZStack {
            LinearGradient(
                colors: [Color(hex: "#0D9488"), Color(hex: "#065F56")],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 24) {
                Image("SplashLogo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 120, height: 120)
                    .scaleEffect(logoScale)
                    .opacity(logoOpacity)

                Text("Smarter scheduling.\nStronger teams.")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)
                    .opacity(taglineOpacity)
            }

            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Text("✦")
                        .font(.system(size: 16))
                        .foregroundColor(.white.opacity(0.7))
                        .rotationEffect(.degrees(sparkleRotation))
                        .opacity(sparkleOpacity)
                        .padding(32)
                }
            }
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                logoScale = 1.0
                logoOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.5).delay(0.4)) {
                taglineOpacity = 1.0
            }
            withAnimation(.easeIn(duration: 0.3).delay(0.7)) {
                sparkleOpacity = 1.0
            }
            withAnimation(.linear(duration: 2.0).delay(0.7)) {
                sparkleRotation = 360
            }
        }
    }
}
