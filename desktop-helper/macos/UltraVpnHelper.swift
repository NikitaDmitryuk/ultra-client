import Darwin
import Foundation

private let socketPath = "/var/run/ultra-client-helper.sock"
private var singBoxProcess: Process?

private struct Request: Decodable {
    let command: String
    let singBox: String?
    let config: String?
    let stdout: String?
    let stderr: String?
    let workingDirectory: String?
}

private struct Response: Encodable {
    let ok: Bool
    let message: String?
    let pid: Int32?
}

private func allowedUid() -> uid_t? {
    guard let raw = ProcessInfo.processInfo.environment["ULTRA_ALLOWED_UID"],
          let parsed = UInt32(raw) else {
        return nil
    }
    return uid_t(parsed)
}

private func send(_ response: Response, to fd: Int32) {
    let encoder = JSONEncoder()
    let data =
        (try? encoder.encode(response))
            ?? Data(#"{"ok":false,"message":"response encoding failed"}"#.utf8)
    _ = data.withUnsafeBytes { bytes in
        write(fd, bytes.baseAddress, data.count)
    }
    _ = "\n".withCString { ptr in write(fd, ptr, 1) }
}

private func makeError(_ message: String) -> Response {
    Response(ok: false, message: message, pid: nil)
}

private func isRunning(_ process: Process?) -> Bool {
    process?.isRunning == true
}

private func start(_ request: Request) -> Response {
    if isRunning(singBoxProcess) {
        return Response(ok: true, message: "already running", pid: singBoxProcess?.processIdentifier)
    }
    guard let singBox = request.singBox, let config = request.config else {
        return makeError("missing singBox or config")
    }
    let process = Process()
    process.executableURL = URL(fileURLWithPath: singBox)
    process.arguments = ["run", "-c", config]
    if let workingDirectory = request.workingDirectory {
        process.currentDirectoryURL = URL(fileURLWithPath: workingDirectory)
    }
    if let stdout = request.stdout {
        FileManager.default.createFile(atPath: stdout, contents: nil)
        chmod(stdout, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH)
        process.standardOutput = try? FileHandle(forWritingTo: URL(fileURLWithPath: stdout))
    }
    if let stderr = request.stderr {
        FileManager.default.createFile(atPath: stderr, contents: nil)
        chmod(stderr, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH)
        process.standardError = try? FileHandle(forWritingTo: URL(fileURLWithPath: stderr))
    }
    do {
        try process.run()
        singBoxProcess = process
        return Response(ok: true, message: nil, pid: process.processIdentifier)
    } catch {
        return makeError(error.localizedDescription)
    }
}

private func stop() -> Response {
    guard let process = singBoxProcess else {
        return Response(ok: true, message: "not running", pid: nil)
    }
    if process.isRunning {
        process.terminate()
        Thread.sleep(forTimeInterval: 1.0)
        if process.isRunning {
            kill(process.processIdentifier, SIGKILL)
        }
    }
    singBoxProcess = nil
    return Response(ok: true, message: nil, pid: nil)
}

private func status() -> Response {
    Response(
        ok: true,
        message: isRunning(singBoxProcess) ? "running" : "stopped",
        pid: singBoxProcess?.processIdentifier
    )
}

private func handle(client fd: Int32) {
    defer { close(fd) }

    var peerUid = uid_t()
    var peerGid = gid_t()
    if getpeereid(fd, &peerUid, &peerGid) != 0 {
        send(makeError("unable to verify peer credentials"), to: fd)
        return
    }
    if let allowed = allowedUid(), peerUid != allowed {
        send(makeError("unauthorized peer uid \(peerUid)"), to: fd)
        return
    }

    var buffer = [UInt8](repeating: 0, count: 64 * 1024)
    let count = read(fd, &buffer, buffer.count)
    guard count > 0 else {
        send(makeError("empty request"), to: fd)
        return
    }
    let data = Data(buffer.prefix(count))
    guard let request = try? JSONDecoder().decode(Request.self, from: data) else {
        send(makeError("invalid request json"), to: fd)
        return
    }

    switch request.command {
    case "start":
        send(start(request), to: fd)
    case "stop":
        send(stop(), to: fd)
    case "status":
        send(status(), to: fd)
    default:
        send(makeError("unknown command \(request.command)"), to: fd)
    }
}

private func runServer() {
    unlink(socketPath)
    let serverFd = socket(AF_UNIX, SOCK_STREAM, 0)
    guard serverFd >= 0 else {
        fatalError("socket failed")
    }

    var addr = sockaddr_un()
    addr.sun_family = sa_family_t(AF_UNIX)
    _ = socketPath.withCString { path in
        withUnsafeMutablePointer(to: &addr.sun_path.0) { ptr in
            strcpy(UnsafeMutableRawPointer(ptr).assumingMemoryBound(to: CChar.self), path)
        }
    }

    let bindResult = withUnsafePointer(to: &addr) { pointer in
        pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) { sockaddrPointer in
            bind(serverFd, sockaddrPointer, socklen_t(MemoryLayout<sockaddr_un>.size))
        }
    }
    guard bindResult == 0 else {
        fatalError("bind \(socketPath) failed")
    }
    chmod(socketPath, S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH | S_IWOTH)

    guard listen(serverFd, 16) == 0 else {
        fatalError("listen failed")
    }
    while true {
        let clientFd = accept(serverFd, nil, nil)
        if clientFd >= 0 {
            handle(client: clientFd)
        }
    }
}

runServer()
