import SwiftUI
import Shared

@main
struct iOSApp: App {

    init() {
        // Starts the shared dependency graph (and remembering the known device) once per process.
        MainViewControllerKt.startIosApp()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
