import SwiftUI
import UIKit
import Shared

/// Hosts the Compose Multiplatform UI; every screen lives in the shared Kotlin code.
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        // Compose draws edge to edge and applies the safe-area insets itself.
        ComposeView().ignoresSafeArea()
    }
}
