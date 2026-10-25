#!/usr/bin/env python3
"""
Zwift Click v2 Cross-Platform BLE Worker
Connects to Zwift Click / Zwift Play / Cog handlebar controllers via Bleak (BlueZ, CoreBluetooth, WinRT).
Emits STATE and PACKET lines to stdout for consumption by DesktopZwiftBleController in AnkiRideBridge.
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
RIDE_ON_HANDSHAKE = b"RideOn"   # 526964654f6e

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


try:
    from bleak import BleakScanner, BleakClient
    from bleak.exc import BleakError
except ImportError:
    log("ERROR", "Python module 'bleak' is not installed. Please run: pip install bleak")
    emit_state("ERROR", "bleak_not_installed")
    # Keep process alive with periodic warnings so caller sees status without immediate crash loop
    while True:
        time.sleep(10)
        log("WARN", "Waiting for 'bleak' to be installed (pip install bleak)...")


class ZwiftBleWorker:
    def __init__(self, pinned_right: str = "", pinned_left: str = "", right_only: bool = True):
        self.pinned_right = pinned_right.strip().upper() if pinned_right else ""
        self.pinned_left = pinned_left.strip().upper() if pinned_left else ""
        self.right_only = right_only
        self.running = True
        self.active_clients = {}  # mac -> BleakClient
        self.connected_macs = set()

    def is_zwift_device(self, device, adv_data) -> bool:
        uuids = [u.lower() for u in (adv_data.service_uuids or [])]
        if ZWIFT_FC82_SERVICE_UUID.lower() in uuids or ZWIFT_LEGACY_SERVICE_UUID.lower() in uuids:
            return True

        if adv_data.manufacturer_data and ZWIFT_MANUFACTURER_ID in adv_data.manufacturer_data:
            return True

        name = (device.name or adv_data.local_name or "").strip()
        if "zwift click" in name.lower() or "zwift play" in name.lower() or name.lower().startswith("zwift"):
            return True

        return False

    def is_left_pod(self, adv_data) -> bool:
        if adv_data.manufacturer_data and ZWIFT_MANUFACTURER_ID in adv_data.manufacturer_data:
            data = adv_data.manufacturer_data[ZWIFT_MANUFACTURER_ID]
            if len(data) > 0 and data[0] == DEVICE_ID_LEFT_POD:
                return True
        return False

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
                devices = await BleakScanner.discover(timeout=4.0, return_adv=True)

                for mac, (device, adv_data) in devices.items():
                    if not self.running:
                        break
                    mac_upper = mac.upper()
                    if mac_upper in self.connected_macs:
                        continue
                    if not self.is_mac_allowed(mac_upper):
                        continue
                    if not self.is_zwift_device(device, adv_data):
                        continue

                    is_left = self.is_left_pod(adv_data)
                    if self.right_only and is_left:
                        log("INFO", f"Skipping left pod {mac_upper} because right_only=true")
                        continue

                    # If right_only mode and we already connected one device while iterating
                    if self.right_only and len(self.connected_macs) >= 1:
                        break

                    # Connect to this device
                    asyncio.create_task(self.connect_and_stream(device, mac_upper, is_left))

                await asyncio.sleep(1.0)

            except BleakError as be:
                log("WARN", f"Bleak scan error: {be}")
                emit_state("ERROR", str(be))
                await asyncio.sleep(3.0)
            except Exception as e:
                log("ERROR", f"Unexpected scan exception: {e}")
                await asyncio.sleep(3.0)

    async def connect_and_stream(self, device, mac: str, is_left: bool):
        if mac in self.connected_macs:
            return
        log("INFO", f"Connecting GATT to {mac} (is_left={is_left})...")
        emit_state("CONNECTING", mac)

        def on_disconnect(client):
            log("WARN", f"Device disconnected: {mac}")
            self.connected_macs.discard(mac)
            self.active_clients.pop(mac, None)
            self.update_connection_state(mac)

        try:
            client = BleakClient(device, disconnected_callback=on_disconnect, timeout=12.0)
            connected = await client.connect()
            if not connected:
                log("WARN", f"Failed to connect to {mac}")
                emit_state("DISCONNECTED", mac)
                return

            self.active_clients[mac] = client
            emit_state("HANDSHAKING", mac)
            log("INFO", f"Connected to {mac}. Performing 'RideOn' handshake...")

            # Write "RideOn" (0x526964654F6E) to Control Point Characteristic
            try:
                await client.write_gatt_char(CONTROL_POINT_CHAR_UUID, RIDE_ON_HANDSHAKE, response=False)
            except Exception as we:
                log("WARN", f"Handshake write with response=False failed ({we}), trying response=True...")
                try:
                    await client.write_gatt_char(CONTROL_POINT_CHAR_UUID, RIDE_ON_HANDSHAKE, response=True)
                except Exception as we2:
                    log("ERROR", f"RideOn handshake failed on {mac}: {we2}")
                    await client.disconnect()
                    return

            # Subscribe to Measurement notifications
            def notification_handler(sender, data: bytearray):
                hex_str = data.hex().upper()
                emit_packet(mac, hex_str)

            await client.start_notify(MEASUREMENT_CHAR_UUID, notification_handler)
            self.connected_macs.add(mac)
            log("INFO", f"Handshake complete & subscribed to notifications on {mac}")
            self.update_connection_state(mac)

            # Keep task alive while client is connected
            while client.is_connected and self.running:
                await asyncio.sleep(0.5)

        except Exception as e:
            log("ERROR", f"Error during connection to {mac}: {e}")
            self.connected_macs.discard(mac)
            self.active_clients.pop(mac, None)
            emit_state("DISCONNECTED", mac)
        finally:
            self.connected_macs.discard(mac)
            self.active_clients.pop(mac, None)
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


def main():
    parser = argparse.ArgumentParser(description="Zwift Click v2 BLE Worker")
    parser.add_argument("--pinned-right", default="", help="Pinned MAC address for Right Pod")
    parser.add_argument("--pinned-left", default="", help="Pinned MAC address for Left Pod")
    parser.add_argument("--right-only", default="true", help="Connect only to single/right pod (true/false)")
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
        asyncio.create_task(worker.stop())

    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, handle_signal)
        except (NotImplementedError, RuntimeError):
            # Windows or restricted thread
            pass

    try:
        loop.run_until_complete(worker.run())
    except KeyboardInterrupt:
        loop.run_until_complete(worker.stop())
    finally:
        loop.close()


if __name__ == "__main__":
    main()
