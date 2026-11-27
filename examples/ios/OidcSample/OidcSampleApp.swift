import OidcSampleShared
import SwiftUI

@main
struct OidcSampleApp: App {

    init() {
        OidcSampleIos.shared.initialize()
    }

    var body: some Scene {
        WindowGroup {
            OidcScreen()
        }
    }
}
