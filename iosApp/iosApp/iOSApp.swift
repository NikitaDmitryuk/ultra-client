import SwiftUI
import SharedPresentation

@main
struct iOSApp: App {
    init() {
        IosModuleKt.initKoinIos(
            clipboardReader: IosClipboardReaderBridge(),
            installedAppsProvider: IosInstalledAppsProviderBridge()
        )
    }
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
