#!/bin/bash
# Stops a stuck emulator / earlier run, then runs store-screenshots.command with a fresh emulator.
cd "$(dirname "$0")"
pkill -f "store-screenshots.command" 2>/dev/null
pkill -f "adb wait-for-device" 2>/dev/null
pkill -f "qemu-system" 2>/dev/null
sleep 5
~/Library/Android/sdk/platform-tools/adb kill-server 2>/dev/null
sleep 2
./store-screenshots.command
