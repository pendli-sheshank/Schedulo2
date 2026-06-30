import SwiftUI
import Network

class ConnectivityManager: NSObject, ObservableObject {
    @Published var isOnline = true
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "connectivityMonitor")

    override init() {
        super.init()
        monitor.pathUpdateHandler = { [weak self] path in
            DispatchQueue.main.async {
                self?.isOnline = path.status == .satisfied
            }
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }
}

struct ConnectivityIndicator: View {
    @ObservedObject var connectivityManager: ConnectivityManager

    var body: some View {
        if !connectivityManager.isOnline {
            HStack(spacing: 8) {
                Image(systemName: "wifi.slash")
                    .font(.system(size: 12, weight: .semibold))
                Text("No Internet Connection")
                    .font(.system(size: 12, weight: .semibold))
                Spacer()
            }
            .foregroundColor(.white)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .background(
                LinearGradient(
                    gradient: Gradient(colors: [
                        Color(red: 0.8, green: 0.2, blue: 0.2),
                        Color(red: 0.7, green: 0.15, blue: 0.15)
                    ]),
                    startPoint: .leading,
                    endPoint: .trailing
                )
            )
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }
}

#Preview {
    ConnectivityIndicator(connectivityManager: ConnectivityManager())
}
