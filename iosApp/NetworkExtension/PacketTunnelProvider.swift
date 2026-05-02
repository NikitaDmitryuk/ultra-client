import NetworkExtension
// import LibXray  // Uncomment after adding LibXray.xcframework to the NetworkExtension target

class PacketTunnelProvider: NEPacketTunnelProvider {

    private var xrayStarted = false

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        guard let configData = options?["config"] as? String else {
            completionHandler(makeError(code: 1, message: "Missing Xray config in start options"))
            return
        }

        let configPath = writeConfigToSharedContainer(configData)

        let settings = NEPacketTunnelNetworkSettings(tunnelRemoteAddress: "127.0.0.1")
        settings.iPv4Settings = NEIPv4Settings(
            addresses: ["10.0.0.1"],
            subnetMasks: ["255.255.255.255"]
        )
        settings.iPv4Settings?.includedRoutes = [NEIPv4Route.default()]
        settings.dnsSettings = NEDNSSettings(servers: ["198.18.0.3"])
        settings.mtu = 1500

        setTunnelNetworkSettings(settings) { [weak self] error in
            guard let self = self, error == nil else {
                completionHandler(error)
                return
            }
            // Start Xray with compiled LibXray.xcframework
            // let err = LibxrayStartXray(configPath)
            // Replace the stub below with LibxrayStartXray once AAR is linked:
            let err = self.stubStartXray(configPath: configPath)
            if err.isEmpty {
                self.xrayStarted = true
                completionHandler(nil)
            } else {
                completionHandler(self.makeError(code: 2, message: err))
            }
        }
    }

    override func stopTunnel(
        with reason: NEProviderStopReason,
        completionHandler: @escaping () -> Void
    ) {
        if xrayStarted {
            // LibxrayStopXray()
            xrayStarted = false
        }
        completionHandler()
    }

    override func handleAppMessage(
        _ messageData: Data,
        completionHandler: ((Data?) -> Void)?
    ) {
        completionHandler?(Data("ok".utf8))
    }

    // MARK: - Private helpers

    private func writeConfigToSharedContainer(_ config: String) -> String {
        guard let containerURL = FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: "group.io.nikdmitryuk.ultraclient"
        ) else {
            return ""
        }
        let path = containerURL.appendingPathComponent("xray_config.json").path
        try? config.write(toFile: path, atomically: true, encoding: .utf8)
        return path
    }

    private func makeError(code: Int, message: String) -> NSError {
        NSError(
            domain: "io.nikdmitryuk.ultraclient",
            code: code,
            userInfo: [NSLocalizedDescriptionKey: message]
        )
    }

    // Stub: remove this when LibXray is linked
    private func stubStartXray(configPath: String) -> String {
        NSLog("PacketTunnelProvider: stubStartXray called with config at \(configPath)")
        return ""
    }
}
