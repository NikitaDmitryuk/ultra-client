import NetworkExtension
// import LibBox  // Link sing-box Apple framework in the NetworkExtension target.

class PacketTunnelProvider: NEPacketTunnelProvider {

    private var singBoxStarted = false

    override func startTunnel(
        options: [String: NSObject]?,
        completionHandler: @escaping (Error?) -> Void
    ) {
        guard let configData = options?["config"] as? String else {
            completionHandler(makeError(code: 1, message: "Missing sing-box config in start options"))
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
            // Start sing-box with the linked Apple framework.
            // Replace the stub below with the libbox/sing-box entrypoint once the framework is linked.
            let err = self.stubStartSingBox(configPath: configPath)
            if err.isEmpty {
                self.singBoxStarted = true
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
        if singBoxStarted {
            // Stop the linked sing-box runtime here.
            singBoxStarted = false
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
        let path = containerURL.appendingPathComponent("sing-box-config.json").path
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

    // Stub: remove this when the sing-box Apple framework is linked.
    private func stubStartSingBox(configPath: String) -> String {
        NSLog("PacketTunnelProvider: stubStartSingBox called with config at \(configPath)")
        return ""
    }
}
