#!/usr/bin/env python3
"""
Zwift Click v2 Cross-Platform BLE Worker
Connects to Zwift Click / Zwift Play / Cog handlebar controllers via Bleak (BlueZ, CoreBluetooth, WinRT).
Emits STATE, PACKET, and LOG lines to stdout for consumption by DesktopZwiftBleController in AnkiRideBridge.
"""

import sys
import os
import time
import asyncio
import argparse
import signal

# Service and Characteristic UUIDs for Zwift Click & Zwift Play
ZWIFT_FC82_SERVICE_UUID = "0000fc82-0000-1000-8000-00805f9b34fb"
ZWIFT_LEGACY_SERVICE_UUID = "00000001-19ca-4651-86e5-fa29dcdd09d1"
MEASUREMENT_CHAR_UUID = "00000002-19ca-4651-86e5-fa29dcdd09d1"
CONTROL_POINT_CHAR_UUID = "00000003-19ca-4651-86e5-fa29dcdd09d1"
COMMAND_RESPONSE_CHAR_UUID = "00000004-19ca-4651-86e5-fa29dcdd09d1"

ZWIFT_MANUFACTURER_ID = 0x094A  # 2378 decimal
RIDE_ON_HANDSHAKE = b"RideOn"   # 526964654F6E

DEVICE_ID_RIGHT_POD = 0x07
DEVICE_ID_LEFT_POD = 0x08
DEVICE_ID_CLICK = 0x09


def log(level: str, msg: str):
    print(f"LOG {level} {msg}", flush=True)


def emit_state(state: str, mac: str = ""):
    if mac:
        print(f"STATE {state} {mac}", flush=True)
    else:
        print(f"STATE {state}", flush=True)


def emit_packet(mac: str, hex_bytes: str):
    print(f"PACKET {mac} {hex_bytes}", flush=True)


if "--help" in sys.argv or "-h" in sys.argv:
    parser = argparse.ArgumentParser(description="Zwift Click v2 BLE Worker")
    parser.add_argument("--pinned-right", default="", help="Pinned MAC address for Right Pod")
    parser.add_argument("--pinned-left", default="", help="Pinned MAC address for Left Pod")
    parser.add_argument("--right-only", default="true", help="Connect only to single/right pod (true/false)")
    parser.add_argument("--scan-test", action="store_true", help="Run in scan-test mode (scan, connect, stream for duration, exit 0)")
    parser.add_argument("--duration", type=int, default=15, help="Duration for scan-test mode in seconds (default 15)")
    parser.parse_args()
    sys.exit(0)


try:
    from bleak import BleakScanner, BleakClient
    from bleak.exc import BleakError
except ImportError:
    log("ERROR", "Python module 'bleak' is not installed. Please run: pip install bleak")
    emit_state("ERROR", "bleak_not_installed")
    if "--scan-test" in sys.argv:
        sys.exit(1)
    # Keep process alive with periodic warnings so caller sees status without immediate crash loop
    while True:
        time.sleep(10)
        log("WARN", "Waiting for 'bleak' to be installed (pip install bleak)...")


def is_zwift_device(device, adv_data) -> bool:
    # 1. Check Service UUIDs (FC82 or 00000001 legacy)
    uuids = [str(u).lower() for u in (adv_data.service_uuids or [])]
    if ZWIFT_FC82_SERVICE_UUID.lower() in uuids or ZWIFT_LEGACY_SERVICE_UUID.lower() in uuids:
        return True

    # 2. Check Manufacturer ID 0x094A (2378)
    if adv_data.manufacturer_data and ZWIFT_MANUFACTURER_ID in adv_data.manufacturer_data:
        return True

    # 3. Check device name prefixes: Zwift Click, Zwift Ride, Zwift Play, or starts with Zwift
    name = (device.name or adv_data.local_name or "").strip().lower()
    for prefix in ("zwift click", "zwift ride", "zwift play", "zwift"):
        if name.startswith(prefix) or prefix in name:
            return True

    return False


def is_left_pod(adv_data) -> bool:
    if adv_data.manufacturer_data and ZWIFT_MANUFACTURER_ID in adv_data.manufacturer_data:
        data = adv_data.manufacturer_data[ZWIFT_MANUFACTURER_ID]
        if len(data) > 0 and data[0] == DEVICE_ID_LEFT_POD:
            return True
    return False


async def setup_and_handshake(client: BleakClient, mac: str) -> bool:
    """
    Discovers characteristics, subscribes to measurement (and additional notify) characteristics,
    and performs the 'RideOn' handshake write to control_point_char.
    """
    emit_state("HANDSHAKING", mac)
    log("INFO", f"Connected to {mac}. Performing 'RideOn' handshake & characteristic discovery...")

    measurement_char = None
    control_point_char = None
    zwift_services = []

    for service in client.services:
        s_uuid = service.uuid.lower()
        if (s_uuid == ZWIFT_FC82_SERVICE_UUID.lower() or
            s_uuid == ZWIFT_LEGACY_SERVICE_UUID.lower() or
            "fc82" in s_uuid or
            "19ca-4651-86e5-fa29dcdd09d1" in s_uuid):
            zwift_services.append(service)

    search_services = zwift_services if zwift_services else client.services

    for service in search_services:
        for char in service.characteristics:
            c_uuid = char.uuid.lower()
            if c_uuid == MEASUREMENT_CHAR_UUID.lower():
                measurement_char = char
            elif c_uuid == CONTROL_POINT_CHAR_UUID.lower():
                control_point_char = char

    # Fallback to any notify/write characteristic in Zwift services if UUIDs differ
    if not measurement_char:
        for service in zwift_services:
            for char in service.characteristics:
                props = [p.lower() for p in char.properties]
                if "notify" in props or "indicate" in props:
                    measurement_char = char
                    log("INFO", f"Discovered fallback measurement characteristic {char.uuid} on {mac}")
                    break
            if measurement_char:
                break

    if not control_point_char:
        for service in zwift_services:
            for char in service.characteristics:
                props = [p.lower() for p in char.properties]
                if "write-without-response" in props or "write" in props:
                    control_point_char = char
                    log("INFO", f"Discovered fallback control point characteristic {char.uuid} on {mac}")
                    break
            if control_point_char:
                break

    def notification_handler(sender, data: bytearray):
        hex_str = data.hex().upper()
        emit_packet(mac, hex_str)

    # Subscribe to measurement_char
    if measurement_char:
        try:
            await client.start_notify(measurement_char, notification_handler)
            log("INFO", f"Subscribed to measurement notifications ({measurement_char.uuid}) on {mac}")
        except Exception as ne:
            log("WARN", f"Failed to subscribe to measurement char on {mac}: {ne}")
    else:
        log("WARN", f"Measurement characteristic not found on {mac}")

    # Also check if any additional notify characteristics exist on the Zwift service and subscribe to them
    for service in zwift_services:
        for char in service.characteristics:
            if measurement_char and char.uuid.lower() == measurement_char.uuid.lower():
                continue
            props = [p.lower() for p in char.properties]
            if "notify" in props or "indicate" in props:
                try:
                    await client.start_notify(char, notification_handler)
                    log("INFO", f"Subscribed to additional notify characteristic {char.uuid} on {mac}")
                except Exception as ne:
                    log("WARN", f"Failed to subscribe to additional notify char {char.uuid} on {mac}: {ne}")

    # Write "RideOn" (0x526964654F6E) to control point characteristic
    if control_point_char:
        props = [p.lower() for p in control_point_char.properties]
        initial_response = "write-without-response" not in props
        try:
            await client.write_gatt_char(control_point_char, RIDE_ON_HANDSHAKE, response=initial_response)
            log("INFO", f"RideOn handshake write succeeded (response={initial_response}) on {mac}")
        except Exception as we:
            fallback_response = not initial_response
            log("WARN", f"RideOn write with response={initial_response} failed ({we}), retrying with response={fallback_response}...")
            try:
                await client.write_gatt_char(control_point_char, RIDE_ON_HANDSHAKE, response=fallback_response)
                log("INFO", f"RideOn handshake fallback succeeded (response={fallback_response}) on {mac}")
            except Exception as we2:
                log("ERROR", f"RideOn handshake failed on {mac}: {we2}")
                return False
    else:
        log("WARN", f"No control point characteristic found on {mac} for RideOn handshake")

    return True


class ZwiftBleWorker:
    def __init__(self, pinned_right: str = "", pinned_left: str = "", right_only: bool = True):
        self.pinned_right = pinned_right.strip().upper() if pinned_right else ""
        self.pinned_left = pinned_left.strip().upper() if pinned_left else ""
        self.right_only = right_only
        self.running = True
        self.active_clients = {}  # mac -> BleakClient
        self.connected_macs = set()

    def is_mac_allowed(self, mac: str) -> bool:
        mac_upper = mac.upper()
        if self.pinned_right and self.pinned_left:
            return mac_upper == self.pinned_right or mac_upper == self.pinned_left
        if self.pinned_right:
            return mac_upper == self.pinned_right
        if self.pinned_left:
            return mac_upper == self.pinned_left
        return True

    def update_connection_state(self, changed_mac: str = ""):
        count = len(self.connected_macs)
        if count >= 2:
            emit_state("CONNECTED_BOTH_PODS", changed_mac)
        elif count == 1:
            first_mac = next(iter(self.connected_macs))
            emit_state("CONNECTED_RIGHT_ONLY", first_mac)
        else:
            emit_state("DISCONNECTED", changed_mac)

    async def run(self):
        log("INFO", f"Starting Zwift BLE Worker (right_only={self.right_only}, pinned_right='{self.pinned_right}', pinned_left='{self.pinned_left}')")
        emit_state("SCANNING")

        while self.running:
            try:
                # If right_only mode and we already have 1 connected pod, idle and monitor
                if self.right_only and len(self.connected_macs) >= 1:
                    await asyncio.sleep(1.0)
                    continue

                # If both pods connected, idle and monitor
                if len(self.connected_macs) >= 2:
                    await asyncio.sleep(1.0)
                    continue

                emit_state("SCANNING")

                found_device = None
                found_adv = None
                found_event = asyncio.Event()

                def detection_callback(device, adv_data):
                    nonlocal found_device, found_adv
                    if not self.running:
                        return
                    mac_upper = device.address.upper()
                    if mac_upper in self.connected_macs or mac_upper in self.active_clients:
                        return
                    if not self.is_mac_allowed(mac_upper):
                        return
                    if not is_zwift_device(device, adv_data):
                        return

                    left = is_left_pod(adv_data)
                    if self.right_only and left:
                        log("INFO", f"Skipping left pod {mac_upper} because right_only=true")
                        return

                    found_device = device
                    found_adv = adv_data
                    found_event.set()

                # Explicit BleakScanner with detection callback
                scanner = BleakScanner(detection_callback=detection_callback)
                try:
                    await scanner.start()
                    try:
                        await asyncio.wait_for(found_event.wait(), timeout=4.0)
                    except asyncio.TimeoutError:
                        pass
                finally:
                    # ALWAYS call scanner.stop() completely followed by 150ms settle delay
                    try:
                        await scanner.stop()
                    except Exception as se:
                        log("WARN", f"Scanner stop exception: {se}")

                await asyncio.sleep(0.15)

                if found_device is not None and self.running:
                    mac_upper = found_device.address.upper()
                    is_left = is_left_pod(found_adv)
                    await self.connect_device(found_device, mac_upper, is_left)

                await asyncio.sleep(0.5)

            except BleakError as be:
                log("WARN", f"Bleak scan error: {be}")
                emit_state("ERROR", str(be))
                await asyncio.sleep(3.0)
            except Exception as e:
                log("ERROR", f"Unexpected scan exception: {e}")
                await asyncio.sleep(3.0)

    async def connect_device(self, device, mac: str, is_left: bool):
        if mac in self.connected_macs or mac in self.active_clients:
            return

        log("INFO", f"Connecting GATT to {mac} (is_left={is_left})...")
        emit_state("CONNECTING", mac)

        def on_disconnect(client):
            log("WARN", f"Device disconnected: {mac}")
            self.connected_macs.discard(mac)
            self.active_clients.pop(mac, None)
            emit_state("DISCONNECTED", mac)
            self.update_connection_state(mac)

        try:
            client = BleakClient(device, disconnected_callback=on_disconnect, timeout=10.0)
            connected = await client.connect(timeout=10.0)
            if not connected and not client.is_connected:
                log("WARN", f"Failed to connect to {mac}")
                emit_state("DISCONNECTED", mac)
                return

            self.active_clients[mac] = client

            success = await setup_and_handshake(client, mac)
            if not success:
                try:
                    await client.disconnect()
                except Exception:
                    pass
                self.active_clients.pop(mac, None)
                self.connected_macs.discard(mac)
                emit_state("DISCONNECTED", mac)
                self.update_connection_state(mac)
                return

            self.connected_macs.add(mac)
            log("INFO", f"Handshake complete & subscribed to notifications on {mac}")
            self.update_connection_state(mac)

        except Exception as e:
            log("ERROR", f"Error during connection to {mac}: {e}")
            self.connected_macs.discard(mac)
            self.active_clients.pop(mac, None)
            emit_state("DISCONNECTED", mac)
            self.update_connection_state(mac)

    async def stop(self):
        self.running = False
        log("INFO", "Stopping BLE worker and disconnecting devices...")
        for mac, client in list(self.active_clients.items()):
            try:
                await client.disconnect()
            except Exception:
                pass
        self.active_clients.clear()
        self.connected_macs.clear()
        emit_state("DISCONNECTED")


async def run_scan_test(duration: int = 15):
    """
    --scan-test CLI Mode:
    Scans up to 15 seconds for a physical Zwift Click v2 / Ride / Play controller.
    Stops scanner, waits 0.15s, connects, prints 'RideOn' handshake result (STATE CONNECTED_RIGHT_ONLY <mac>),
    and streams live PACKET <mac> <hex> lines for 15 seconds as buttons are pressed, then cleanly disconnects and exits 0.
    """
    log("INFO", f"Starting Zwift BLE scan-test mode (duration={duration}s)...")
    emit_state("SCANNING")

    found_device = None
    found_adv = None
    found_event = asyncio.Event()

    def detection_callback(device, adv_data):
        nonlocal found_device, found_adv
        if not is_zwift_device(device, adv_data):
            return
        log("INFO", f"Discovered candidate Zwift device: {device.address} (name='{device.name}', local_name='{adv_data.local_name}')")
        found_device = device
        found_adv = adv_data
        found_event.set()

    scanner = BleakScanner(detection_callback=detection_callback)
    try:
        await scanner.start()
        try:
            await asyncio.wait_for(found_event.wait(), timeout=float(duration))
        except asyncio.TimeoutError:
            log("WARN", f"No Zwift device found within {duration} seconds.")
    except BleakError as be:
        log("WARN", f"Bleak scan error: {be}")
        emit_state("ERROR", str(be))
    except Exception as e:
        log("ERROR", f"Error during scan: {e}")
        emit_state("ERROR", str(e))
    finally:
        try:
            await scanner.stop()
        except Exception:
            pass

    await asyncio.sleep(0.15)

    if found_device is None:
        emit_state("DISCONNECTED")
        log("INFO", "Scan test completed (no device found). Exiting 0.")
        sys.exit(0)

    mac = found_device.address.upper()
    emit_state("CONNECTING", mac)
    log("INFO", f"Connecting to {mac}...")

    def on_disconnect(client):
        log("WARN", f"Device disconnected: {mac}")
        emit_state("DISCONNECTED", mac)

    client = BleakClient(found_device, disconnected_callback=on_disconnect, timeout=10.0)
    try:
        await client.connect(timeout=10.0)
    except Exception as ce:
        log("ERROR", f"Failed to connect to {mac}: {ce}")
        emit_state("DISCONNECTED", mac)
        sys.exit(0)

    if not client.is_connected:
        log("ERROR", f"Client not connected to {mac}")
        emit_state("DISCONNECTED", mac)
        sys.exit(0)

    success = await setup_and_handshake(client, mac)
    if not success:
        log("ERROR", f"Handshake failed on {mac}")
        try:
            await client.disconnect()
        except Exception:
            pass
        emit_state("DISCONNECTED", mac)
        sys.exit(0)

    # Print "RideOn" handshake result
    emit_state("CONNECTED_RIGHT_ONLY", mac)
    log("INFO", f"Streaming live packets for {duration} seconds (press buttons now)...")

    # Streams live PACKET <mac> <hex> lines for duration seconds as buttons are pressed
    end_time = time.time() + float(duration)
    while time.time() < end_time and client.is_connected:
        await asyncio.sleep(0.1)

    log("INFO", "Scan test complete. Disconnecting cleanly...")
    try:
        await client.disconnect()
    except Exception:
        pass
    emit_state("DISCONNECTED", mac)
    sys.exit(0)


def main():
    parser = argparse.ArgumentParser(description="Zwift Click v2 BLE Worker")
    parser.add_argument("--pinned-right", default="", help="Pinned MAC address for Right Pod")
    parser.add_argument("--pinned-left", default="", help="Pinned MAC address for Left Pod")
    parser.add_argument("--right-only", default="true", help="Connect only to single/right pod (true/false)")
    parser.add_argument("--scan-test", action="store_true", help="Run in scan-test mode (scan, connect, stream for duration, exit 0)")
    parser.add_argument("--duration", type=int, default=15, help="Duration for scan-test mode in seconds (default 15)")
    args = parser.parse_args()

    right_only_bool = args.right_only.strip().lower() in ("true", "1", "yes")

    worker = ZwiftBleWorker(
        pinned_right=args.pinned_right,
        pinned_left=args.pinned_left,
        right_only=right_only_bool
    )

    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

    def handle_signal():
        log("INFO", "Received termination signal, shutting down...")
        if args.scan_test:
            sys.exit(0)
        else:
            asyncio.create_task(worker.stop())

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, handle_signal)
        except (NotImplementedError, RuntimeError):
            # Windows or restricted thread
            pass

    try:
        if args.scan_test:
            loop.run_until_complete(run_scan_test(duration=args.duration))
        else:
            loop.run_until_complete(worker.run())
    except KeyboardInterrupt:
        if not args.scan_test:
            loop.run_until_complete(worker.stop())
    finally:
        loop.close()


if __name__ == "__main__":
    main()
