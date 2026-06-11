import SwiftUI

// MARK: - Brand Colors

extension Color {
    // Primary palette (matching Android)
    static let primaryGreen = Color(hex: "#2D6A4F")
    static let secondaryGreen = Color(hex: "#74C69D")
    static let accentBlue = Color(hex: "#3B82F6")
    static let accentOrange = Color(hex: "#F59E0B")

    // Dark mode
    static let darkBackground = Color(hex: "#0F1117")
    static let darkSurface = Color(hex: "#1A1D24")
    static let darkSurfaceVariant = Color(hex: "#242831")
    static let darkOutline = Color(hex: "#2E3240")
    static let darkOnBackground = Color(hex: "#E8EAED")
    static let darkOnSurfaceVariant = Color(hex: "#9CA3AF")

    // Light mode
    static let lightBackground = Color(hex: "#F8F9FA")
    static let lightSurface = Color(hex: "#FFFFFF")
    static let lightOutline = Color(hex: "#E8E8ED")
    static let lightOnBackground = Color(hex: "#1A1C1E")
    static let lightSurfaceVariant = Color(hex: "#F0F1F5")
    static let lightOnSurfaceVariant = Color(hex: "#6B7280")
}

// MARK: - Hex Initializer

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 3: // RGB (12-bit)
            (a, r, g, b) = (255, (int >> 8) * 17, (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6: // RGB (24-bit)
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8: // ARGB (32-bit)
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - View Modifiers

struct CardStyle: ViewModifier {
    @Environment(\.colorScheme) var colorScheme

    func body(content: Content) -> some View {
        content
            .padding(16)
            .background(colorScheme == .dark ? Color.darkSurface : Color.lightSurface)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(colorScheme == .dark ? Color.darkOutline : Color.lightOutline, lineWidth: 1)
            )
    }
}

extension View {
    func cardStyle() -> some View {
        modifier(CardStyle())
    }
}

// MARK: - Gradient Helpers

extension LinearGradient {
    static var primaryGradient: LinearGradient {
        LinearGradient(
            colors: [Color.primaryGreen, Color.secondaryGreen],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    static var accentGradient: LinearGradient {
        LinearGradient(
            colors: [Color.accentBlue, Color.primaryGreen],
            startPoint: .leading,
            endPoint: .trailing
        )
    }
}

// MARK: - Adaptive Colors

struct AppColors {
    let colorScheme: ColorScheme

    var background: Color { colorScheme == .dark ? .darkBackground : .lightBackground }
    var surface: Color { colorScheme == .dark ? .darkSurface : .lightSurface }
    var surfaceVariant: Color { colorScheme == .dark ? .darkSurfaceVariant : .lightSurfaceVariant }
    var outline: Color { colorScheme == .dark ? .darkOutline : .lightOutline }
    var onBackground: Color { colorScheme == .dark ? .darkOnBackground : .lightOnBackground }
    var onSurfaceVariant: Color { colorScheme == .dark ? .darkOnSurfaceVariant : .lightOnSurfaceVariant }
}

extension EnvironmentValues {
    var appColors: AppColors {
        AppColors(colorScheme: colorScheme)
    }
}
