import SwiftUI
import ComposeApp

@main
struct iOSApp: App {
    init() {
        Modules_iosKt.doInitKoin()
    }
    
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}