# Android TUN VPN (Kotlin)

A virtual TUN environment for Android supporting **IPv4** and **UDP** networking.

## Features

- Handles **IPv4** and **UDP** packets
- Routes packets between a **TUN interface** and UDP sockets
- Modular architecture with separate components for **TUN reading**, **network I/O**, and **flow management**
- Automatic cleanup of inactive flows
- Concurrent processing using **threads** and **NIO**

## License

MIT License
